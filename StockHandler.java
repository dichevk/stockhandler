
package com.stockhandler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder;
import com.amazonaws.services.simpleemail.model.*;
import org.apache.http.client.fluent.Request;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.logging.Logger;

public class StockHandler implements RequestHandler<ScheduledEvent, String> {

    private static final String ALPHA_VANTAGE_API_KEY = ""; //YOUR_ALPHA_VANTAGE_API_KEY
    private static final String STOCK_SYMBOL = ""; //YOUR_STOCK_SYMBOL
    private static final String EMAIL_RECIPIENT = ""; //recipient@example.com
    private static final String BUCKET_NAME = ""; //your-s3-bucket-name
    private static final Logger LOGGER = Logger.getLogger("StockHandler");

    public String handleRequest(ScheduledEvent event, Context context) {
        double previousClose = getPreviousClose(STOCK_SYMBOL);
        double currentPrice = getCurrentPrice(STOCK_SYMBOL);

        if (previousClose > 0 && currentPrice > 0) {
            double percentageIncrease = ((currentPrice - previousClose) / previousClose) * 100;

            if (percentageIncrease >= 10.0) {
                String emailSubject = "Stock Alert: " + STOCK_SYMBOL + " increased by 10%!";
                String emailBody = "The stock price of " + STOCK_SYMBOL + " has increased by 10% since the last close.";

                sendEmail(EMAIL_RECIPIENT, emailSubject, emailBody);
                writeToS3(BUCKET_NAME, emailSubject, emailBody);
                LOGGER.info("Email sent for " + STOCK_SYMBOL);
                return "Email sent for " + STOCK_SYMBOL;
            }
        }

        LOGGER.info("No significant increase for " + STOCK_SYMBOL);
        return "No significant increase for " + STOCK_SYMBOL;
    }

    private double getPreviousClose(String symbol) {
        try {
            String url = "https://www.alphavantage.co/query?function=TIME_SERIES_DAILY&symbol="
                    + symbol + "&apikey=" + ALPHA_VANTAGE_API_KEY;

            String response = Request.Get(url).execute().returnContent().asString();
            JSONObject jsonObject = new JSONObject(response);
            JSONObject timeSeries = jsonObject.getJSONObject("Time Series (Daily)");
            String latestDate = timeSeries.keys().next();
            JSONObject latestData = timeSeries.getJSONObject(latestDate);

            return latestData.getDouble("4. close");
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    private double getCurrentPrice(String symbol) {
        try {
            String url = "https://www.alphavantage.co/query?function=GLOBAL_QUOTE&symbol="
                    + symbol + "&apikey=" + ALPHA_VANTAGE_API_KEY;

            String response = Request.Get(url).execute().returnContent().asString();
            JSONObject jsonObject = new JSONObject(response);
            JSONObject globalQuote = jsonObject.getJSONObject("Global Quote");

            return globalQuote.getDouble("05. price");
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    private void sendEmail(String recipient, String subject, String body) {
        try {
            AmazonSimpleEmailService client = AmazonSimpleEmailServiceClientBuilder.standard().build();
            SendEmailRequest request = new SendEmailRequest()
                    .withDestination(new Destination().withToAddresses(recipient))
                    .withMessage(new Message()
                            .withBody(new Body().withText(new Content().withCharset("UTF-8").withData(body)))
                            .withSubject(new Content().withCharset("UTF-8").withData(subject)))
                    .withSource("sender@example.com"); // Replace with your sender email

            client.sendEmail(request);
        } catch (Exception ex) {
            LOGGER.severe("Error sending email: " + ex.getMessage());
        }
    }

    private void writeToS3(String bucketName, String key, String content) {
        try {
            AmazonS3 s3Client = AmazonS3ClientBuilder.defaultClient();
            InputStream inputStream = new ByteArrayInputStream(content.getBytes());
            s3Client.putObject(bucketName, key, inputStream, null);
        } catch (Exception e) {
            LOGGER.severe("Error writing to S3: " + e.getMessage());
        }
    }
}
