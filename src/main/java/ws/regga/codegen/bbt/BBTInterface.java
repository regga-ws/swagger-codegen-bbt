package ws.regga.codegen.bbt;

import java.util.Map;

public interface BBTInterface {
	
	public void requestAndAssertResponse(String url, String httpMethod, Map<String, String> requestHeaders, String requestBody, Map<String, String> expectedResponseHeaders, String expectedResponseBody, Integer expectedResponseCode) throws Exception;

}