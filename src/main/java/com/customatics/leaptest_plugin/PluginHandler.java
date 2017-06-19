package com.customatics.leaptest_plugin;

import com.customatics.leaptest_plugin.model.Case;
import com.customatics.leaptest_plugin.model.InvalidSchedule;
import com.customatics.leaptest_plugin.model.Schedule;
import com.customatics.leaptest_plugin.model.ScheduleCollection;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;
import hudson.model.TaskListener;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import java.io.*;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;


public final class PluginHandler {

    private static PluginHandler pluginHandler = null;

    private PluginHandler(){}

    public static PluginHandler getInstance()
    {
        if( pluginHandler == null ) pluginHandler = new PluginHandler();

        return pluginHandler;
    }


    public String getJunitReportFilePath(String jenkinsWorkspacePath, String reportFileName)
    {
        if(reportFileName.isEmpty() || "".equals(reportFileName))
        {
            reportFileName = "report.xml";
        }

        if(!reportFileName.contains(".xml"))
        {
            reportFileName +=".xml";
        }
        return String.format("%1$s/%2$s",jenkinsWorkspacePath,reportFileName);
    }

    public ArrayList<String> getRawScheduleList(String rawScheduleIds, String rawScheduleTitles)
    {
        ArrayList<String> rawScheduleList = new ArrayList<>();

        String[] schidsArray = rawScheduleIds.split("\n|, |,");
        String[] testsArray = rawScheduleTitles.split("\n|, |,");

        for(int i = 0; i < schidsArray.length; i++)
        {
            rawScheduleList.add(schidsArray[i]);
        }
        for(int i = 0; i < testsArray.length; i++)
        {
            rawScheduleList.add(testsArray[i]);
        }

        return rawScheduleList;
    }

    public int getTimeDelay(String rawTimeDelay)
    {
        int defaultTimeDelay = 3;
        if(!rawTimeDelay.isEmpty() || !"".equals(rawTimeDelay))
            return Integer.parseInt(rawTimeDelay);
        else
            return defaultTimeDelay;
    }


    public HashMap<String, String> getSchedulesIdTitleHashMap(
            String leaptestAddress,
            ArrayList<String> rawScheduleList,
            final TaskListener listener,
            ScheduleCollection buildResult,
            ArrayList<InvalidSchedule> invalidSchedules
    )
    {

        HashMap<String, String> schedulesIdTitleHashMap = new HashMap<>();

        String scheduleListUri = String.format(Messages.GET_ALL_AVAILABLE_SCHEDULES_URI, leaptestAddress);

        try {

            AsyncHttpClient client = new AsyncHttpClient();
            Response response = client.prepareGet(scheduleListUri).execute().get();
            client = null;

            if (response.getStatusCode() != 200) {
                String errormessage = String.format(Messages.ERROR_CODE_MESSAGE, response.getStatusCode(), response.getStatusText());
                listener.error(errormessage);
                throw new Exception(errormessage);
                //TODO: if 400, if 500 and so on
            } else {
                JsonParser parser = new JsonParser();
                JsonArray jsonScheduleList = parser.parse(response.getResponseBody()).getAsJsonArray();


                for (String rawSchedule : rawScheduleList) {
                    boolean successfullyMapped = false;
                    for (JsonElement jsonScheduleElement : jsonScheduleList) {
                        JsonObject jsonSchedule = jsonScheduleElement.getAsJsonObject();

                        String Id = Utils.defaultStringIfNull(jsonSchedule.get("Id"), "null Id");
                        String Title = Utils.defaultStringIfNull(jsonSchedule.get("Title"), "null Title");
                        //TODO check if schedules can be without titles

                        if (Id.contentEquals(rawSchedule)) {
                            if (!schedulesIdTitleHashMap.containsValue(Title)) {
                                schedulesIdTitleHashMap.put(rawSchedule, Title);
                                buildResult.Schedules.add(new Schedule(rawSchedule, Title));
                                listener.getLogger().println(String.format(Messages.SCHEDULE_DETECTED, Title, rawSchedule));
                            }
                            successfullyMapped = true;
                        }

                        if (Title.contentEquals(rawSchedule)) {
                            if (!schedulesIdTitleHashMap.containsKey(Id)) {
                                schedulesIdTitleHashMap.put(Id, rawSchedule);
                                buildResult.Schedules.add(new Schedule(Id, rawSchedule));
                                listener.getLogger().println(String.format(Messages.SCHEDULE_DETECTED, rawSchedule, Title));
                            }
                            successfullyMapped = true;
                        }
                    }

                    if (!successfullyMapped)
                        invalidSchedules.add(new InvalidSchedule(rawSchedule, Messages.NO_SUCH_SCHEDULE));
                }
            }
        } catch (ConnectException e){
            listener.error( e.getMessage());
        } catch (InterruptedException e) {
            listener.error( e.getMessage());
        } catch (ExecutionException e) {
            listener.error( e.getMessage());
        } catch (IOException e) {
            listener.error( e.getMessage());
        } catch (Exception e) {
            listener.error(Messages.SCHEDULE_TITLE_OR_ID_ARE_NOT_GOT);
            listener.error(e.getMessage());
        } finally {
            return schedulesIdTitleHashMap;
        }
    }

    public boolean runSchedule(
            String leaptestAddress,
            HashMap.Entry<String,String> schedule,
            int currentScheduleIndex,
            final TaskListener listener,
            ScheduleCollection buildResult,
            ArrayList<InvalidSchedule> invalidSchedules
    )
    {
        boolean isSuccessfullyRun = false;

        String uri = String.format(Messages.RUN_SCHEDULE_URI, leaptestAddress, schedule.getKey());

        try {

            AsyncHttpClient client = new AsyncHttpClient();
            Response response = client.preparePut(uri).setBody("").execute().get();
            client = null;

            if (response.getStatusCode() != 204)          // 204 Response means correct schedule launching
            {
                String errormessage = String.format(Messages.ERROR_CODE_MESSAGE, response.getStatusCode(), response.getStatusText());
                listener.error(errormessage);
                buildResult.Schedules.get(currentScheduleIndex).setError(errormessage);
                throw new Exception();
            }

            else
            {
                isSuccessfullyRun = true;
                String successmessage = String.format(Messages.SCHEDULE_RUN_SUCCESS, schedule.getValue(), schedule.getKey());
                buildResult.Schedules.get(currentScheduleIndex).setId(currentScheduleIndex);
                listener.getLogger().println(Messages.SCHEDULE_CONSOLE_LOG_SEPARATOR);
                listener.getLogger().println(successmessage);
            }

        } catch (InterruptedException e) {
            buildResult.Schedules.get(currentScheduleIndex).setError(e.getMessage());
            listener.error(e.getMessage());
        } catch (ExecutionException e) {
            buildResult.Schedules.get(currentScheduleIndex).setError(e.getMessage());
            listener.error(e.getMessage());
        } catch (IOException e) {
            buildResult.Schedules.get(currentScheduleIndex).setError(e.getMessage());
            listener.error(e.getMessage());
        }
        catch (Exception e){
            String errormessage = String.format(Messages.SCHEDULE_RUN_FAILURE,  schedule.getValue(), schedule.getKey());
            listener.error(errormessage);
            listener.error(e.getMessage());
            buildResult.Schedules.get(currentScheduleIndex).setError(errormessage);
            buildResult.Schedules.get(currentScheduleIndex).incErrors();
            invalidSchedules.add(new InvalidSchedule(String.format(Messages.SCHEDULE_FORMAT,schedule.getValue(),schedule.getKey()),buildResult.Schedules.get(currentScheduleIndex).getError()));

        }
        finally {
            return isSuccessfullyRun;
        }
    }

    public boolean getScheduleState(
            String leaptestAddress,
            HashMap.Entry<String,String> schedule,
            int currentScheduleIndex,
            String doneStatusValue,
            final TaskListener listener,
            ScheduleCollection buildResult,
            ArrayList<InvalidSchedule> invalidSchedules
    )
    {
        boolean isScheduleStillRunning = true;

        String uri = String.format(Messages.GET_SCHEDULE_STATE_URI, leaptestAddress, schedule.getKey());

        try {

            AsyncHttpClient client = new AsyncHttpClient();
            Response response = client.prepareGet(uri).execute().get();
            client = null;

            if (response.getStatusCode() != 200) {
                String errormessage = String.format(Messages.ERROR_CODE_MESSAGE, response.getStatusCode(), response.getStatusText());
                listener.error(errormessage);
                buildResult.Schedules.get(currentScheduleIndex).setError(errormessage);
                throw new Exception();
            } else {

                JsonParser parser = new JsonParser();
                JsonObject jsonState = parser.parse(response.getResponseBody()).getAsJsonObject();
                parser = null;

                String ScheduleId = jsonState.get("ScheduleId").getAsString();

                if (isScheduleStillRunning(jsonState))
                    isScheduleStillRunning = true;
                else {
                    isScheduleStillRunning = false;

                    /////////Schedule Info
                    JsonElement jsonLastRun = jsonState.get("LastRun");

                    if (jsonLastRun != null) {
                        JsonObject lastRun = jsonLastRun.getAsJsonObject();

                        String ScheduleTitle = lastRun.get("ScheduleTitle").getAsString();

                        buildResult.Schedules.get(currentScheduleIndex).setTime(parseExecutionTimeToSeconds(lastRun.get("ExecutionTotalTime")));

                        int passedCount = caseStatusCount("PassedCount", lastRun);
                        int failedCount = caseStatusCount("FailedCount", lastRun);
                        int doneCount = caseStatusCount("DoneCount", lastRun);

                        buildResult.Schedules.get(currentScheduleIndex).setPassed(passedCount);
                        buildResult.Schedules.get(currentScheduleIndex).setFailed(failedCount);

                        if (doneStatusValue.contentEquals("Failed"))
                            buildResult.Schedules.get(currentScheduleIndex).addFailed(doneCount);
                        else
                            buildResult.Schedules.get(currentScheduleIndex).addPassed(doneCount);


                        ///////////AutomationRunItemsInfo
                        JsonArray jsonAutomationRunItems = lastRun.get("AutomationRunItems").getAsJsonArray();

                        ArrayList<String> automationRunId = new ArrayList<String>();
                        for (JsonElement jsonAutomationRunItem : jsonAutomationRunItems)
                            automationRunId.add(jsonAutomationRunItem.getAsJsonObject().get("AutomationRunId").getAsString());
                        ArrayList<String> statuses = new ArrayList<String>();
                        for (JsonElement jsonAutomationRunItem : jsonAutomationRunItems)
                            statuses.add(jsonAutomationRunItem.getAsJsonObject().get("Status").getAsString());
                        ArrayList<String> elapsed = new ArrayList<String>();
                        for (JsonElement jsonAutomationRunItem : jsonAutomationRunItems)
                            elapsed.add(jsonAutomationRunItem.getAsJsonObject().get("Elapsed").getAsString());
                        ArrayList<String> environments = new ArrayList<String>();
                        for (JsonElement jsonAutomationRunItem : jsonAutomationRunItems)
                            environments.add(jsonAutomationRunItem.getAsJsonObject().get("Environment").getAsJsonObject().get("Title").getAsString());

                        ArrayList<String> caseTitles = new ArrayList<String>();
                        for (JsonElement jsonAutomationRunItem : jsonAutomationRunItems) {
                            String caseTitle = Utils.defaultStringIfNull(jsonAutomationRunItem.getAsJsonObject().get("Case").getAsJsonObject().get("Title"), "Null case Title");
                            if (caseTitle.contentEquals("Null case Title"))
                                caseTitles.add(caseTitles.get(caseTitles.size() - 1));
                            else
                                caseTitles.add(caseTitle);
                        }


                        for (int i = 0; i < jsonAutomationRunItems.size(); i++) {

                            //double seconds = jsonArray.getJSONObject(i).getDouble("TotalSeconds");
                            double seconds = parseExecutionTimeToSeconds(elapsed.get(i));

                            listener.getLogger().println(Messages.CASE_CONSOLE_LOG_SEPARATOR);

                            if (statuses.get(i).contentEquals("Failed") || (statuses.get(i).contentEquals("Done") && doneStatusValue.contentEquals("Failed"))) {
                                JsonArray jsonKeyframes = jsonAutomationRunItems.get(i).getAsJsonObject().get("Keyframes").getAsJsonArray();

                                //KeyframeInfo
                                ArrayList<String> keyFrameTimeStamps = new ArrayList<String>();
                                for (JsonElement jsonKeyFrame : jsonKeyframes)
                                    keyFrameTimeStamps.add(jsonKeyFrame.getAsJsonObject().get("Timestamp").getAsString());
                                ArrayList<String> keyFrameLogMessages = new ArrayList<String>();
                                for (JsonElement jsonKeyFrame : jsonKeyframes)
                                    keyFrameLogMessages.add(jsonKeyFrame.getAsJsonObject().get("LogMessage").getAsString());


                                listener.getLogger().println(String.format(Messages.CASE_INFORMATION, caseTitles.get(i), statuses.get(i), elapsed.get(i)));

                                String fullstacktrace = "";
                                int currentKeyFrameIndex = 0;

                                for (JsonElement jsonKeyFrame : jsonKeyframes) {
                                    String level = Utils.defaultStringIfNull(jsonKeyFrame.getAsJsonObject().get("Level"), "");
                                    if (!level.contentEquals("") && !level.contentEquals("Trace")) {
                                        String stacktrace = String.format(Messages.CASE_STACKTRACE_FORMAT, keyFrameTimeStamps.get(currentKeyFrameIndex), keyFrameLogMessages.get(currentKeyFrameIndex));
                                        listener.getLogger().println(stacktrace);
                                        fullstacktrace += stacktrace;
                                        fullstacktrace += "&#xA;"; //fullstacktrace += '\n';
                                    }

                                    currentKeyFrameIndex++;
                                }

                                fullstacktrace += "Environment: " + environments.get(i);
                                listener.getLogger().println("Environment: " + environments.get(i));
                                buildResult.Schedules.get(currentScheduleIndex).Cases.add(new Case(caseTitles.get(i), statuses.get(i), seconds, fullstacktrace, ScheduleTitle/* + "[" + ScheduleId + "]"*/));
                            } else {
                                listener.getLogger().println(String.format(Messages.CASE_INFORMATION, caseTitles.get(i), statuses.get(i), elapsed.get(i)));
                                buildResult.Schedules.get(currentScheduleIndex).Cases.add(new Case(caseTitles.get(i), statuses.get(i), seconds, ScheduleTitle/* + "[" + ScheduleId + "]"*/));
                            }
                        }

                        if (buildResult.Schedules.get(currentScheduleIndex).getFailed() > 0) {
                            buildResult.Schedules.get(currentScheduleIndex).setStatus("Failed");
                        } else {
                            buildResult.Schedules.get(currentScheduleIndex).setStatus("Passed");
                        }
                    } else {
                        String errorMessage = String.format(Messages.SCHEDULE_HAS_NO_CASES_XML, ScheduleId, response.getResponseBody());
                        buildResult.Schedules.get(currentScheduleIndex).setError(errorMessage);
                        buildResult.Schedules.get(currentScheduleIndex).incErrors();
                        listener.error(String.format(Messages.SCHEDULE_HAS_NO_CASES, ScheduleId, response.getResponseBody()));
                        invalidSchedules.add(new InvalidSchedule(ScheduleId, errorMessage));
                    }

                }
            }
        } catch (InterruptedException e) {
            listener.error(e.getMessage());
            buildResult.Schedules.get(currentScheduleIndex).setError(e.getMessage());
        } catch (ExecutionException e) {
            listener.error(e.getMessage());
            buildResult.Schedules.get(currentScheduleIndex).setError(e.getMessage());
        } catch (IOException e) {
            listener.error(e.getMessage());
            buildResult.Schedules.get(currentScheduleIndex).setError(e.getMessage());
        } catch (Exception e)
        {
            String errorMessage = String.format(Messages.SCHEDULE_STATE_FAILURE, schedule.getValue(), schedule.getKey());
            buildResult.Schedules.get(currentScheduleIndex).setError(errorMessage);
            buildResult.Schedules.get(currentScheduleIndex).incErrors();
            listener.error(errorMessage);
            listener.error(e.getMessage());
            invalidSchedules.add(new InvalidSchedule(String.format(Messages.SCHEDULE_FORMAT,schedule.getValue(),schedule.getKey()),buildResult.Schedules.get(currentScheduleIndex).getError()));
        } finally {
            return isScheduleStillRunning;
        }
    }

    public void createJUnitReport(String JUnitReportFilePath, final TaskListener listener, ScheduleCollection buildResult)
    {
        try
        {
            File reportFile = new File(JUnitReportFilePath);
            if(!reportFile.exists()) reportFile.createNewFile();

            StringWriter writer = new StringWriter();
            JAXBContext context = JAXBContext.newInstance(ScheduleCollection.class);

            Marshaller m = context.createMarshaller();
            m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            m.marshal(buildResult, writer);

            StringWriter formattedWriter  =  new StringWriter();
            formattedWriter.append(writer.getBuffer().toString().replace("&amp;#xA","&#xA"));

            writer = null;

            try (PrintStream out = new PrintStream(new FileOutputStream(reportFile.getAbsolutePath()))) {
                out.print(formattedWriter);
                out.close();
            }

        }
        catch (FileNotFoundException e) {
            listener.error(Messages.REPORT_FILE_NOT_FOUND);
            listener.error(e.getMessage());
        } catch (IOException e) {
            listener.error(Messages.REPORT_FILE_CREATION_FAILURE);
            listener.error(e.getMessage());
        } catch (JAXBException e) {
            listener.error(Messages.REPORT_FILE_CREATION_FAILURE);
            listener.error(e.getMessage());
        }
    }

    private boolean isScheduleStillRunning(JsonObject jsonState)
    {
        String status = Utils.defaultStringIfNull(jsonState.get("Status"), "Finished");

        if (status.contentEquals("Running") || status.contentEquals("Queued"))
            return true;
        else
            return false;

    }

    private double parseExecutionTimeToSeconds(String rawExecutionTime)
    {
        String ExecutionTotalTime[] = rawExecutionTime.split(":|\\.");

        return  Double.parseDouble(ExecutionTotalTime[0]) * 60 * 60 +  //hours
                Double.parseDouble(ExecutionTotalTime[1]) * 60 +        //minutes
                Double.parseDouble(ExecutionTotalTime[2]) +             //seconds
                Double.parseDouble("0." + ExecutionTotalTime[3]);     //milliseconds
    }

    private double parseExecutionTimeToSeconds(JsonElement rawExecutionTime)
    {
        if(rawExecutionTime != null) {
            String ExecutionTotalTime[] = rawExecutionTime.getAsString().split(":|\\.");

            return Double.parseDouble(ExecutionTotalTime[0]) * 60 * 60 +  //hours
                    Double.parseDouble(ExecutionTotalTime[1]) * 60 +        //minutes
                    Double.parseDouble(ExecutionTotalTime[2]) +             //seconds
                    Double.parseDouble("0." + ExecutionTotalTime[3]);     //milliseconds
        }
        else
            return 0;
    }

    private int caseStatusCount(String statusName, JsonObject lastRun)
    {
        Integer temp =  Utils.defaultIntIfNull(lastRun.get(statusName), 0);
        return temp.intValue();
    }
}
