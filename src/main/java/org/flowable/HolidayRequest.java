package org.flowable;

import org.flowable.engine.*;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.impl.cfg.StandaloneProcessEngineConfiguration;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class HolidayRequest {
    public static void main(String[] args) {
        //创建一个独立(standalone)配置对象
        ProcessEngineConfiguration cfg = new StandaloneProcessEngineConfiguration()
                //传递了一个内存H2数据库实例的JDBC连接参数，JVM重启后消失
            .setJdbcUrl("jdbc:h2:mem:flowable;DB_CLOSE_DELAY=-1")
            .setJdbcUsername("sa")
            .setJdbcPassword("")
            .setJdbcDriver("org.h2.Driver")
                //设置为true，确保在JDBC参数连接的数据库中，数据库表结构不存在时，会创建相应的表结构
            .setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);
        //创建ProcessEngine对象
        ProcessEngine processEngine = cfg.buildProcessEngine();
        //获取RepositoryService用于部署流程定义
        RepositoryService repositoryService = processEngine.getRepositoryService();
        //将流程定义部署至Flowable引擎,并调用deploy()方法执行
        Deployment deployment = repositoryService.createDeployment()
            .addClasspathResource("holiday-request.bpmn20.xml")
            .deploy();
        //验证流程定义已经部署在引擎中
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                .deploymentId(deployment.getId())
                .singleResult();
        System.err.println("找到流程定义："+processDefinition.getName());
        //模拟请求参数传递
        Scanner scanner= new Scanner(System.in);
        System.out.println("Who are you?");
        String employee = scanner.nextLine();
        System.out.println("How many holidays do you want to request?");
        Integer nrOfHolidays = Integer.valueOf(scanner.nextLine());
        System.out.println("Why do you need them?");
        String description = scanner.nextLine();
        //使用RuntimeService启动一个流程实例
        RuntimeService runtimeService = processEngine.getRuntimeService();
        Map<String, Object> variables = new HashMap<String, Object>();
        variables.put("employee", employee);
        variables.put("nrOfHolidays", nrOfHolidays);
        variables.put("description", description);
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey("holidayRequest", variables);
        //获得实际的任务列表
        TaskService taskService = processEngine.getTaskService();
        List<Task> tasks = taskService.createTaskQuery().taskCandidateGroup("managers").list();
        //返回’managers’组的任务
        System.out.println("You have " + tasks.size() + " tasks:");
        for (int i=0; i<tasks.size(); i++) {
            System.out.println((i+1) + ") " + tasks.get(i).getName());
        }
        //使用任务Id获取特定流程实例的变量，并在屏幕上显示实际的申请
        System.out.println("Which task would you like to complete?");
        int taskIndex = Integer.valueOf(scanner.nextLine());
        Task task = tasks.get(taskIndex - 1);
        Map<String, Object> processVariables = taskService.getVariables(task.getId());
        System.out.println(processVariables.get("employee") + " wants " +
                processVariables.get("nrOfHolidays") + " of holidays. Do you approve this?");
        //完成任务后使用map传递aproved变量，用于顺序流的条件中使用
        boolean approved = scanner.nextLine().toLowerCase().equals("y");
        variables = new HashMap<String, Object>();
        variables.put("approved", approved);
        taskService.complete(task.getId(), variables);

        HistoryService historyService = processEngine.getHistoryService();
        List<HistoricActivityInstance> activities =
                historyService.createHistoricActivityInstanceQuery()
                        .processInstanceId(processInstance.getId())
                        .finished()
                        .orderByHistoricActivityInstanceEndTime().asc()
                        .list();

        for (HistoricActivityInstance activity : activities) {
            System.out.println(activity.getActivityId() + " took "
                    + activity.getDurationInMillis() + " milliseconds");
        }
    }
}
