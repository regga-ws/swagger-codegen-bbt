package ws.regga.codegen.bbt;

import java.io.File;
import java.util.ArrayList;
import java.util.Map;

import com.samskivert.mustache.Template;

import io.swagger.codegen.CliOption;
import io.swagger.codegen.CodegenType;
import io.swagger.codegen.SupportingFile;
import ws.regga.codegen.util.ReggaCodegen;

public class JavaBBTCodegen extends ReggaCodegen {
    
	private static ArrayList<String> SUPPORTING_FILE_STORY_LIST = new ArrayList<String>();
	private static int SUPPORTING_FILE_CONTEXT_COUNTER = -1;
	private static String SUPPORTING_FILE_CONTEXT_STORYID = null;
	private static String SUPPORTING_FILE_CONTEXT_CLASSNAME = null;
	
    public JavaBBTCodegen() {    	
        super();
        cliOptions.add(new CliOption("testPackage", "Target package for generated tests"));     
        cliOptions.add(new CliOption("testClassAnnotations", "Annotations to add for each generated test class"));
        cliOptions.add(new CliOption("testClassAdditionalContent", "Content to add - constructor, attributes, methods - within each generated test class"));
        cliOptions.add(new CliOption("testParentClass", "Parent test class for generated classes, default is 'TestBase'"));
        // TODO support outputFolder
    }
    
    private static String toTestFileName(String storyId) {
    	return camelize(storyId) + "Test";
    }

	@Override
    public void processOpts() {
        super.processOpts();

        testPackage = additionalProperties.get("testPackage") != null ? additionalProperties.get("testPackage").toString() : "io.swagger.client.test";
        String testFolder = "src" + File.separator + "test" + File.separator + "java" + File.separator + testPackage.replace(".", "/");
        
        String testParentClassTmp = null;        
        if (additionalProperties.get("testParentClass") != null) {
        	testParentClassTmp = additionalProperties.get("testParentClass").toString();
        }
        else {
        	testParentClassTmp = "TestBase";
        	supportingFiles.add(new SupportingFile("TestBase.mustache", testFolder, "TestBase.java"));  
        }        
        final String testParentClass = testParentClassTmp;

		Map<String, ReggaStory> stories = getReggaStories();
    	for(String storyId : stories.keySet()) {
    		ReggaStory story = stories.get(storyId);
    		for (String tag : story.tags) {
    			if (tag.equalsIgnoreCase("test")) {    				
    				supportingFiles.add(new SupportingFile("StoryTest.mustache", testFolder, toTestFileName(storyId) + ".java"));
    				SUPPORTING_FILE_STORY_LIST.add(storyId);
    	    		break;
    			}
    		}
    	}
    	
        additionalProperties.put("fnIncrementSupportingFileCounter", new CustomLambda() {			
			@Override
			public String getOutput(Template.Fragment frag) {
				SUPPORTING_FILE_CONTEXT_COUNTER++;
				SUPPORTING_FILE_CONTEXT_STORYID = SUPPORTING_FILE_STORY_LIST.get(SUPPORTING_FILE_CONTEXT_COUNTER);
				SUPPORTING_FILE_CONTEXT_CLASSNAME = toTestFileName(SUPPORTING_FILE_CONTEXT_STORYID);
		    	return "";
			}
		});
        
        additionalProperties.put("fnPrintPackage", new CustomLambda() {			
			@Override
			public String getOutput(Template.Fragment frag) {
		    	return testPackage;
			}
		});
        
        additionalProperties.put("fnPrintClassnameAndParent", new CustomLambda() {			
			@Override
			public String getOutput(Template.Fragment frag) {
		    	return SUPPORTING_FILE_CONTEXT_CLASSNAME + " extends " + testParentClass;
			}
		});
        
        additionalProperties.put("fnPrintAnnotations", new CustomLambda() {			
			@Override
			public String getOutput(Template.Fragment frag) {
		    	Object testClassAnnotations = additionalProperties.get("testClassAnnotations");
				String str = testClassAnnotations != null ? testClassAnnotations.toString() : "";
				str = str.replace("{{classname}}", SUPPORTING_FILE_CONTEXT_CLASSNAME);
				return str;
			}
		});
        
        additionalProperties.put("fnPrintAdditionalContent", new CustomLambda() {			
			@Override
			public String getOutput(Template.Fragment frag) {
		    	Object testClassAdditionalContent = additionalProperties.get("testClassAdditionalContent");
				String str = testClassAdditionalContent != null ? testClassAdditionalContent.toString() : "";
				str = str.replace("{{classname}}", SUPPORTING_FILE_CONTEXT_CLASSNAME);
				return str;
			}
		});
        
        additionalProperties.put("fnPrintStoriesAsBBT", new CustomLambda() {			
        	@Override
			public String getOutput(Template.Fragment frag) {        		
        		StringBuilder builder = new StringBuilder();
		    	try {
			    	Map<String, ReggaStory> stories = getReggaStories();
		    		ReggaStory story = stories.get(SUPPORTING_FILE_CONTEXT_STORYID);
		    		if (story == null) {
		    			LOGGER.error("Story not found: " + SUPPORTING_FILE_CONTEXT_STORYID);
		    		}
			    	else {
			    		int snipletCount = 0;
			    		for (String snipletId : story.snipletSequence) {
			    			
			    			ReggaSniplet sniplet = getReggaSniplets().get(snipletId);
			    			String id = sniplet.id;
			    			String title = sniplet.title;			    			
			    			boolean ignore = sniplet.tags != null && sniplet.tags.contains("ignore");
			    			
			    			snipletCount++;
			    			String index = String.format("%06d", snipletCount);
			    			
			    			ReggaSniplet requestSniplet = null;
			    			ReggaSniplet responseSniplet = null;
			    			
			    			if (sniplet.requestSnipletId != null) {
			    				requestSniplet = getReggaSniplets().get(sniplet.requestSnipletId);
			    				responseSniplet = sniplet;
			    			}
			    			else if (sniplet.responseSnipletId != null) {
			    				requestSniplet = sniplet;
			    				responseSniplet = getReggaSniplets().get(sniplet.responseSnipletId);
			    			}
			    			else {
			    				LOGGER.error("Could not distinguish request and response sniplets for test generation");
			    				continue;
			    			}
			    			
							StringBuilder exampleBuilder = new StringBuilder();
							
							exampleBuilder.append("    /* " + title + " */\n");
							
							exampleBuilder.append("    @Test\n");				
							if (ignore) exampleBuilder.append("    @Ignore\n");	
							exampleBuilder.append("    public void bbt_" + index + "_" + sanitizeName(id) + "() throws Exception {\n\n");
							
							exampleBuilder.append("        String url = \"" + requestSniplet.url + "\";\n");
							
							exampleBuilder.append("        String httpMethod = \"" + requestSniplet.requestMethod + "\";\n");
							
							exampleBuilder.append("        Map<String, String> requestHeaders = new HashMap<String, String>();\n");		
							exampleBuilder.append("        requestHeaders.put(\"Content-Type\", \"" + requestSniplet.contentType + "\");\n");
							exampleBuilder.append("        requestHeaders.put(\"Accept\", \"" + requestSniplet.contentType + "\");\n");					
							for (String key : requestSniplet.headers.keySet()) {
								exampleBuilder.append("        requestHeaders.put(\"" + key + "\", \"" + requestSniplet.headers.get(key) + "\");\n");
							}
							
							exampleBuilder.append("        Map<String, String> expectedResponseHeaders = new HashMap<String, String>();\n");							
							for (String key : responseSniplet.headers.keySet()) {
								exampleBuilder.append("        expectedResponseHeaders.put(\"" + key + "\", \"" + responseSniplet.headers.get(key) + "\");\n");
							}

							if (requestSniplet.data != null) {
								//if (requestSniplet.data instanceof String) TODO support other objects than string
								exampleBuilder.append("        String requestBody = \"" + requestSniplet.data.toString().replace("\"", "\\\"") + "\";\n");
							}
							else exampleBuilder.append("        String requestBody = null;\n");
							
							if (responseSniplet.data != null) {
								//if (responseSniplet.data instanceof String) TODO support other objects than string
								exampleBuilder.append("        String expectedResponseBody = \"" + responseSniplet.data.toString().replace("\"", "\\\"") + "\";\n");
							}
							else exampleBuilder.append("        String expectedResponseBody = null;\n");
							
							exampleBuilder.append("        Integer expectedResponseCode = " + responseSniplet.responseCode + ";\n\n");

							exampleBuilder.append("        requestAndAssertResponse(url, httpMethod, requestHeaders, requestBody, expectedResponseHeaders, expectedResponseBody, expectedResponseCode);\n");
							exampleBuilder.append("    }\n\n");
							
							builder.append(exampleBuilder);
			    		}
		    		}
		    	}
		    	catch (Exception e) {
		    		e.printStackTrace();
		    	}
		    	
				return builder.toString();
			}
		});
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