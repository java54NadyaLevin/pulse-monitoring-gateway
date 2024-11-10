package telran.pulse.monitoring;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.*;
import java.util.logging.*;
import org.json.JSONObject;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.DynamodbEvent;
import com.amazonaws.services.lambda.runtime.events.models.dynamodb.AttributeValue;
import software.amazon.awssdk.services.dynamodb.*;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest.Builder;
import static telran.pulse.monitoring.Constants.*;
record Range(int min, int max) {

}

public class App {

	static DynamoDbClient client = DynamoDbClient.builder().build();
	static Builder request;
	static Logger logger = Logger.getLogger("pulse-value-analyzer");
	static {
		loggerSetUp();
		
	}

	public void handleRequest(DynamodbEvent event, Context context) {
		request = PutItemRequest.builder().tableName(ABNORMAL_VALUES_TABLE_NAME);
		event.getRecords().forEach(r -> {
			Map<String, AttributeValue> map = r.getDynamodb().getNewImage();
			if (map == null) {
				logger.warning("No new image found");
			} else if (r.getEventName().equals("INSERT")) {
				processPulseValue(map);
			} else {
				logger.warning(String.format("The event isn't INSERT but %s", r.getEventName()));
			}

		});
	}

	private static void loggerSetUp() {
		Level loggerLevel = getLoggerLevel();
		LogManager.getLogManager().reset();
		Handler handler = new ConsoleHandler();
		logger.setLevel(loggerLevel);
		handler.setLevel(Level.FINEST);
		logger.addHandler(handler);
	}

	private static Level getLoggerLevel() {
		String levelStr = System.getenv()
		.getOrDefault(LOGGER_LEVEL_ENV_VARIABLE, DEFAULT_LOGGER_LEVEL);
		Level res = null;
		try {
			res = Level.parse(levelStr);
		} catch (Exception e) {
			res = Level.parse(DEFAULT_LOGGER_LEVEL);
		}
		return res;
	}

	private void processPulseValue(Map<String, AttributeValue> map) {
		int value = Integer.parseInt(map.get(VALUE_ATTRIBUTE).getN());
		int patientId = Integer.parseInt(map.get(PATIENT_ID_ATTRIBUTE).getN());
		try {
			String apiGatewayUrl = System.getenv(API_GATEWAY_URL);
			new URI(apiGatewayUrl);
			Range range = getRange(patientId, apiGatewayUrl);
			if (value > range.max() || value < range.min()) {
				processAbnormalPulseValue(map);
			}
		} catch (Exception e) {
			
			logger.warning(e.getMessage());
		}
		
	}

    private Range getRange(int patientId, String apiGatewayUrl) throws Exception{
		HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(new URI(apiGatewayUrl 
		+ "?" + PATIENT_ID_ATTRIBUTE + "=" + Integer.toString(patientId))).build();
        HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
        JSONObject jsonObject = new JSONObject(response.body());
	    return new Range(jsonObject.getInt("min"), jsonObject.getInt("max"));
	}

	private void processAbnormalPulseValue(Map<String, AttributeValue> map) {
		logger.info(getLogMessage(map));
		client.putItem(request.item(getPutItemMap(map)).build());
	}

	private Map<String, software.amazon.awssdk.services.dynamodb.model.AttributeValue> getPutItemMap(
			Map<String, AttributeValue> map) {
		Map<String, software.amazon.awssdk.services.dynamodb.model.AttributeValue> res = new HashMap<>();
		res.put(PATIENT_ID_ATTRIBUTE, software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder()
				.n(map.get(PATIENT_ID_ATTRIBUTE).getN()).build());
		res.put(TIMESTAMP_ATTRIBUTE, software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder()
				.n(map.get(TIMESTAMP_ATTRIBUTE).getN()).build());
		res.put(VALUE_ATTRIBUTE, software.amazon.awssdk.services.dynamodb.model.AttributeValue.builder()
				.n(map.get(VALUE_ATTRIBUTE).getN()).build());
		return res;
	}

	private String getLogMessage(Map<String, AttributeValue> map) {
		return String.format("patientId: %s, value: %s", map.get(PATIENT_ID_ATTRIBUTE).getN(),
				map.get(VALUE_ATTRIBUTE).getN());
	}
}
