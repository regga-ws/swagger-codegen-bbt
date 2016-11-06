package ws.regga.codegenbbt;

import java.io.IOException;
import java.io.Writer;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;

import io.swagger.codegen.CliOption;
import io.swagger.codegen.CodegenConstants;
import io.swagger.codegen.CodegenOperation;
import io.swagger.codegen.CodegenResponse;
import io.swagger.codegen.CodegenType;
import io.swagger.codegen.SupportingFile;
import io.swagger.codegen.examples.ExampleGenerator;
import io.swagger.codegen.languages.AbstractJavaCodegen;
import io.swagger.models.HttpMethod;
import io.swagger.models.Operation;
import io.swagger.models.Response;
import io.swagger.models.Swagger;

public class BBTCodegen extends AbstractJavaCodegen {
	
    private static abstract class CustomLambda implements Mustache.Lambda {
        @Override
        public void execute(Template.Fragment frag, Writer out) throws IOException {
            out.write(getOutput(frag));
        }
        public abstract String getOutput(Template.Fragment frag);
    }

    private Swagger swagger;
    
    public BBTCodegen() {
    	
        super();
        
        embeddedTemplateDir = "templates";
        apiPackage = "io.swagger.client.api";
        
        modelTemplateFiles.clear();
        apiTemplateFiles.clear();
        modelDocTemplateFiles.clear();
        apiDocTemplateFiles.clear();

        cliOptions.add(new CliOption(CodegenConstants.API_PACKAGE, CodegenConstants.API_PACKAGE_DESC));     
        cliOptions.add(new CliOption("testClassAnnotations", "Annotations to add for each generated test class"));
        cliOptions.add(new CliOption("testClassContent", "Content to add - constructor, attributes, methods - within each generated test class"));
        cliOptions.add(new CliOption("mergeTestClasses", "Indicate to merge all test classes into one"));
        cliOptions.add(new CliOption("testBaseModel", "Parent test class for generated classes"));
        // TODO support outputFolder
    }
    
    @Override
    public void processOpts() {
        super.processOpts();

        Object mergeTestClasses = additionalProperties.get("mergeTestClasses");
        if (mergeTestClasses != null && Boolean.valueOf(mergeTestClasses.toString())) {
            apiTestTemplateFiles.clear();
            supportingFiles.add(new SupportingFile("MergedTest.mustache", (testFolder + '/' + apiPackage).replace(".", "/"), "MergedTest.java"));  
        }

        Object testBaseModel = additionalProperties.get("testBaseModel");
        if (testBaseModel != null) {
        	// TODO improve test base model mgt e.g. "NONE"
        }
        else supportingFiles.add(new SupportingFile("TestBase.mustache", (testFolder + '/' + apiPackage).replace(".", "/"), "TestBase.java"));  
        
        additionalProperties.put("fnPrintAnnotations", new CustomLambda() {			
			@Override
			public String getOutput(Template.Fragment frag) {
		    	Object testClassAnnotations = additionalProperties.get("testClassAnnotations");
				return testClassAnnotations != null ? testClassAnnotations.toString() : null;
			}
		});
        
        additionalProperties.put("fnPrintContent", new CustomLambda() {			
			@Override
			public String getOutput(Template.Fragment frag) {
		    	Object testClassContent = additionalProperties.get("testClassContent");
				return testClassContent != null ? testClassContent.toString() : null;
			}
		});
        
        additionalProperties.put("fnParseExamples", new CustomLambda() {			
        	@Override
			@SuppressWarnings("unchecked")
			public String getOutput(Template.Fragment frag) {
				Map<String, String> examples = (Map<String, String>) frag.context();
				String contentType = examples.get("contentType");
				String example = examples.get("example");	
				String responseCode = examples.get("responseCode");
				StringBuilder builder = new StringBuilder();
				if (example != null && contentType != null) {				
					if (contentType.equals("application/json")) {
						try {
							JsonNode json = new ObjectMapper().readTree(example);
							
							// support specific x-code-examples syntax
							if (json.get("x-code-examples") != null) {
								
								Iterator<JsonNode> i = json.get("x-code-examples").elements();
								while (i.hasNext()) {

									JsonNode command = i.next();
									String language = command.get("language").textValue();
									
									// only shell/curl examples are supported for now
									if (!language.equals("shell")) continue;
									
									String id = command.get("id") != null ? command.get("id").textValue() : ""+new Random().nextInt(100000);
									String title = command.get("title").textValue();
									String input = command.get("input").toString();
									String output = command.get("output") != null ? command.get("output").toString() : null;
									String compatibility = command.get("compatibility") != null ? command.get("compatibility").toString().replace("\"", "") : null;									
									
									StringBuilder exampleBuilder = new StringBuilder();
									exampleBuilder.append("    /* " + title + " */\n");
									exampleBuilder.append("    @Test\n");
									
									if (compatibility != null) {
										String version = additionalProperties().get("appVersion").toString();										
										StringTokenizer st1 = new StringTokenizer(compatibility, ".");
										StringTokenizer st2 = new StringTokenizer(version, ".");
										boolean ignoreTest = false;
										try {
											while (st1.hasMoreTokens()) {
												int ver1 = Integer.parseInt(st1.nextToken());
												int ver2 = Integer.parseInt(st2.nextToken());
												if (ver1 > ver2) {
													ignoreTest = true;
													break;
												}
											}
										}
										catch (Exception e) {	
											e.printStackTrace();
										}
										if (ignoreTest) {
											exampleBuilder.append("    @Ignore\n");											
										}
									}
									
									exampleBuilder.append("    public void bbt_" + id + "() throws Exception {\n\n");
									
									String inputContent = null;
									if (input.indexOf("-d '") > 0) {
										inputContent = input.substring(input.indexOf("-d '") + "-d '".length());
										inputContent = inputContent.substring(0, inputContent.indexOf("}'") + "}".length());
									}
									//System.out.println("inputContent = " + inputContent);
									
									String outputContent = null;
									if (output != null) {
										outputContent = output.substring(1, output.length()-1);
									}
									//System.out.println("outputContent = " + inputContent);
									
									String url = input.substring(input.indexOf(" http")).trim();
									url = url.substring(0, url.indexOf('"')).trim();
									//System.out.println("url = " + url);

									String method = null;
									if (input.indexOf("-X GET") > 0) method = "GET";
        							else if (input.indexOf("-X POST") > 0) method = "POST";
        							else if (input.indexOf("-X PUT") > 0) method = "PUT";
        							else if (input.indexOf("-X DELETE") > 0) method = "DELETE";  
									//System.out.println("method = " + method);									
									
									exampleBuilder.append("        String url = \"" + url + "\";\n");
									exampleBuilder.append("        String httpMethod = \"" + method + "\";\n");
									exampleBuilder.append("        Map<String, String> requestHeaders = new HashMap<String, String>();\n");
									
									Pattern pattern = Pattern.compile("-H '(.*?)' ");
									Matcher matcher = pattern.matcher(input);
									while (matcher.find()) {
										String match = matcher.group(0);
										String headerName = match.substring(4, match.indexOf(":")).trim();
										String headerValue = match.substring(match.indexOf(":")+2, match.length()-2).trim();
										exampleBuilder.append("        requestHeaders.put(\"" + headerName + "\", \"" + headerValue + "\");\n");
									}
									
									exampleBuilder.append("        Map<String, String> expectedResponseHeaders = null;\n");

									if (inputContent != null) exampleBuilder.append("        String requestBody = \"" + inputContent + "\";\n");
									else exampleBuilder.append("        String requestBody = null;\n");
									
									if (outputContent != null) exampleBuilder.append("        String expectedResponseBody = \"" + outputContent + "\";\n");
									else exampleBuilder.append("        String expectedResponseBody = null;\n");
									
									exampleBuilder.append("        Integer expectedResponseCode = " + responseCode + ";\n\n");

									exampleBuilder.append("        assertCall(url, httpMethod, requestHeaders, requestBody, expectedResponseHeaders, expectedResponseBody, expectedResponseCode);\n");
									exampleBuilder.append("    }\n");
									
									builder.append(exampleBuilder);
								}
							}
							else {
								// TODO
							}
						} 
						catch (Exception e) {
							e.printStackTrace();
						}
					}
					else if (contentType.equals("application/xml")) {
						// TODO
					}
					else {
						// FIXME only json and xml are supported
						System.out.println("Example contentType not supported: " + contentType);
					}					
				}
				return builder.toString();
			}
		});
    }

    @Override
    public void preprocessSwagger(Swagger swagger) {
    	
		this.swagger = swagger;
    }
    
    @Override
    @SuppressWarnings("unchecked")
	public Map<String, Object> postProcessOperations(Map<String, Object> objs) {
    	
        Map<String, Object> operations = (Map<String, Object>) objs.get("operations");        
        List<CodegenOperation> operationList = (List<CodegenOperation>) operations.get("operation");
        for (CodegenOperation operation : operationList) {
        	
        	List<CodegenResponse> responses = operation.responses;
        	for (CodegenResponse response : responses) {
        		
            	// ensure all operation examples are displayed: display example of first response
            	if (operation.examples == null && operation.responses.size() > 0 && operation.responses.get(0).examples != null && operation.responses.get(0).examples.size() > 0) {
            		Operation operationTmp = swagger.getPaths().get(operation.path).getOperationMap().get(HttpMethod.valueOf(operation.httpMethod));
            		Response responseTmp = operationTmp.getResponses().get(operation.responses.get(0).code);	
            		operation.examples = new ExampleGenerator(swagger.getDefinitions()).generate(responseTmp.getExamples(), operationTmp.getProduces(), responseTmp.getSchema());
            	}
        		
            	if (operation.examples != null) {
	    			for (Map<String, String> example : operation.examples) {
	        			example.put("responseCode", response.code);   
	    			}
            	}
        			
    			// TODO distinguish multiple response codes
        		break;
        	}
        }
        return objs;
    }

    @Override
    public CodegenType getTag() {
        return CodegenType.CLIENT;
    }

    @Override
    public String getName() {
        return "bbtest";
    }

    @Override
    public String getHelp() {
        return "Generates Junit classes to perform Black-Box testing on your API.";
    }

}