package telran.pulse.monitoring;

import java.util.*;
import java.util.logging.*;
import org.json.JSONObject;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import static telran.pulse.monitoring.Constants.*;
/**
 * Handler for requests to Lambda function.
 */
record Range(int min, int max) {

}

public class App implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    static HashMap<String, Range> ranges = new HashMap<>() {
        {
            put("1", new Range(60, 150));
            put("2", new Range(70, 160));
            put("3", new Range(50, 200));
            put("4", new Range(70, 180));
            put("5", new Range(50, 190));

        }
    };
    static Logger logger = Logger.getLogger("range-provider");
	static {
		loggerSetUp();
		
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
    public APIGatewayProxyResponseEvent handleRequest(final APIGatewayProxyRequestEvent input, final Context context) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        Map<String, String> mapParameters = input.getQueryStringParameters();
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent()
                .withHeaders(headers);

        try {
            if (!mapParameters.containsKey("patientId")) {
                logger.warning("no patientId parameter");
            }
            String patientIdStr = mapParameters.get("patientId");
            Range range = ranges.get(patientIdStr);
            if (range == null) {
                logger.warning(patientIdStr + " not found in ranges");
            }

            response
                    .withStatusCode(200)
                    .withBody(getRangeJSON(range));
                logger.fine(String.format("200, range from %s to %s", range.min(), range.max()));
        } catch (IllegalArgumentException e) {
            String errorJSON = getErrorJSON(e.getMessage());
            response
                    .withBody(errorJSON)
                    .withStatusCode(400);
        } catch (IllegalStateException e) {
            String errorJSON = getErrorJSON(e.getMessage());
            response
                    .withBody(errorJSON)
                    .withStatusCode(404);
        }
        return response;
    }

    private String getErrorJSON(String message) {
        JSONObject jsonObj = new JSONObject();
        jsonObj.put("error", message);
        return jsonObj.toString();
    }

    private String getRangeJSON(Range range) {
        JSONObject jsonObj = new JSONObject();
        jsonObj.put("min", range.min());
        jsonObj.put("max", range.max());
        return jsonObj.toString();
    }

}