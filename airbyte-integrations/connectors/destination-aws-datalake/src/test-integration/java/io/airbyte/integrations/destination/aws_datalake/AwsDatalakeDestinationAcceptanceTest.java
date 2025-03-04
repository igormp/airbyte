/*
 * MIT License
 *
 * Copyright (c) 2020 Airbyte
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package io.airbyte.integrations.destination.aws_datalake;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import io.airbyte.commons.io.IOs;
import io.airbyte.commons.json.Jsons;
import java.util.List;
import com.google.common.collect.ImmutableMap;

import com.fasterxml.jackson.databind.JsonNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.airbyte.commons.json.Jsons;
import io.airbyte.integrations.destination.aws_datalake.AwsDatalakeDestinationConfig;
import io.airbyte.integrations.destination.aws_datalake.AthenaHelper;
import io.airbyte.integrations.destination.aws_datalake.GlueHelper;
import io.airbyte.integrations.standardtest.destination.DestinationAcceptanceTest;

// import com.amazonaws.auth.AWSCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.services.athena.model.GetQueryResultsResponse;
import software.amazon.awssdk.services.athena.paginators.GetQueryResultsIterable;
import software.amazon.awssdk.services.athena.model.ColumnInfo;
import software.amazon.awssdk.services.athena.model.Row;
import software.amazon.awssdk.services.athena.model.Datum;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import com.google.common.collect.Maps;

//import com.amazonaws.auth.BasicAWSCredentials;
import software.amazon.awssdk.regions.Region;


public class AwsDatalakeDestinationAcceptanceTest extends DestinationAcceptanceTest {

  private static final String CONFIG_PATH = "secrets/config.json";
  private static final Logger LOGGER = LoggerFactory.getLogger(AwsDatalakeDestinationAcceptanceTest.class);

  private JsonNode configJson;
  private JsonNode configInvalidCredentialsJson;
  protected AwsDatalakeDestinationConfig config;
  private AthenaHelper athenaHelper;
  private GlueHelper glueHelper;

  @Override
  protected String getImageName() {
    return "airbyte/destination-aws-datalake:dev";
  }

  @Override
  protected JsonNode getConfig() {
    // TODO: Generate the configuration JSON file to be used for running the destination during the test
    // configJson can either be static and read from secrets/config.json directly
    // or created in the setup method
    return configJson;
  }

  @Override
  protected JsonNode getFailCheckConfig() {
    JsonNode credentials = Jsons.jsonNode(ImmutableMap.builder()
        .put("credentials_title", "IAM User")
        .put("aws_access_key_id", "wrong-access-key")
        .put("aws_secret_access_key", "wrong-secret")
        .build());

    JsonNode config = Jsons.jsonNode(ImmutableMap.builder()
        .put("aws_account_id", "112233")
        .put("region", "us-east-1")
        .put("bucket_name", "test-bucket")
        .put("bucket_prefix", "test")
        .put("lakeformation_database_name", "lf_db")
        .put("credentials", credentials)
        .build());

    return config;
  }

  @Override
  protected List<JsonNode> retrieveRecords(TestDestinationEnv testEnv,
                                           String streamName,
                                           String namespace,
                                           JsonNode streamSchema)
      throws IOException, InterruptedException {
    // TODO Implement this method to retrieve records which written to the destination by the connector.
    // Records returned from this method will be compared against records provided to the connector
    // to verify they were written correctly
    LOGGER.info(String.format(">>>>>>>>>> namespace = %s, streamName = %s", namespace, streamName));
    // 2. Read from database:table (SELECT *)
    String query = String.format("SELECT * FROM \"%s\".\"%s\"", config.getDatabaseName(), streamName);
    LOGGER.info(String.format(">>>>>>>>>> query = %s", query));
    GetQueryResultsIterable results = athenaHelper.runQuery(config.getDatabaseName(), query);
    // 3. return the rows as a list of JsonNodes

    return parseResults(results);
  }

  protected List<JsonNode> parseResults(GetQueryResultsIterable queryResults) {

    List<JsonNode> processedResults = new ArrayList<>();

    for (GetQueryResultsResponse result : queryResults) {
      List<ColumnInfo> columnInfoList = result.resultSet().resultSetMetadata().columnInfo();
      Iterator<Row> results = result.resultSet().rows().iterator();
      // processRow(results, columnInfoList);
      // first row has column names
      Row colNamesRow = results.next();
      while (results.hasNext()) {
        Map<String, Object> jsonMap = Maps.newHashMap();
        Row r = results.next();
        Iterator<ColumnInfo> colInfoIterator = columnInfoList.iterator();
        Iterator<Datum> datum = r.data().iterator();
        while (colInfoIterator.hasNext() && datum.hasNext()) {
          ColumnInfo colInfo = colInfoIterator.next();
          Datum value = datum.next();
          LOGGER.info(String.format("key = %s, value = %s, type = %s", colInfo.name(), value.varCharValue(), colInfo.type()));
          Object typedFieldValue = getTypedFieldValue(colInfo.type(), value.varCharValue());
          if (typedFieldValue != null) {
            jsonMap.put(colInfo.name(), typedFieldValue);
          }
        }
        processedResults.add(Jsons.jsonNode(jsonMap));
      }
    }
    return processedResults;
  }

  private static Object getTypedFieldValue(String typeName, String varCharValue) {
    if (varCharValue == null)
      return null; 
    return switch (typeName) {
      case "real", "double", "float" -> Double.parseDouble(varCharValue);
      case "varchar" -> varCharValue;
      case "boolean" -> Boolean.parseBoolean(varCharValue);
      case "integer" -> Integer.parseInt(varCharValue);
      default -> null;
    };
  }

  @Override
  protected List<String> resolveIdentifier(String identifier) {
    final List<String> result = new ArrayList<>();
    result.add(identifier);
    result.add(identifier.toLowerCase());
    return result;
  }

  private JsonNode loadJsonFile(String fileName) throws IOException {
    final JsonNode configFromSecrets = Jsons.deserialize(IOs.readFile(Path.of(fileName)));
    return (configFromSecrets);
  }

  @Override
  protected void setup(TestDestinationEnv testEnv) throws IOException {
    configJson = loadJsonFile(CONFIG_PATH);
    

    this.config = AwsDatalakeDestinationConfig.getAwsDatalakeDestinationConfig(configJson);

    AwsBasicCredentials awsCreds = AwsBasicCredentials.create(config.getAccessKeyId(), config.getSecretAccessKey());
    athenaHelper = new AthenaHelper(awsCreds, Region.US_EAST_1, String.format("s3://%s/airbyte_athena/", config.getBucketName()), "AmazonAthenaLakeFormationPreview");
    glueHelper = new GlueHelper(awsCreds, Region.US_EAST_1);
    glueHelper.purgeDatabase(config.getDatabaseName());
  }

  @Override
  protected void tearDown(TestDestinationEnv testEnv) {
    // TODO Implement this method to run any cleanup actions needed after every test case
    // glueHelper.purgeDatabase(config.getDatabaseName());
  }

}
