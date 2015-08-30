/**
* Licensed to the Apache Software Foundation (ASF) under one
* or more contributor license agreements.  See the NOTICE file
* distributed with this work for additional information
* regarding copyright ownership.  The ASF licenses this file
* to you under the Apache License, Version 2.0 (the
* "License"); you may not use this file except in compliance
* with the License.  You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package org.apache.hadoop.yarn.server.resourcemanager.amlauncher;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.service.AbstractService;
import org.apache.hadoop.yarn.event.EventHandler;
import org.apache.hadoop.yarn.server.resourcemanager.RMContext;
import org.apache.hadoop.yarn.server.resourcemanager.rmapp.attempt.RMAppAttempt;

//ApplicationӦ���¼�������
public class ApplicationMasterLauncher extends AbstractService implements
    EventHandler<AMLauncherEvent> {
  private static final Log LOG = LogFactory.getLog(
      ApplicationMasterLauncher.class);
  private final ThreadPoolExecutor launcherPool;
  private LauncherThread launcherHandlingThread;
  
  //�¼�����
  private final BlockingQueue<Runnable> masterEvents
    = new LinkedBlockingQueue<Runnable>();
  //��Դ������������
  protected final RMContext context;
  
  public ApplicationMasterLauncher(RMContext context) {
    super(ApplicationMasterLauncher.class.getName());
    this.context = context;
    //��ʼ���̳߳�
    this.launcherPool = new ThreadPoolExecutor(10, 10, 1, 
        TimeUnit.HOURS, new LinkedBlockingQueue<Runnable>());
    //�½������߳�
    this.launcherHandlingThread = new LauncherThread();
  }
  
  //������������
  @Override
  protected void serviceStart() throws Exception {
    launcherHandlingThread.start();
    super.serviceStart();
  }
  
  //����Ӧ��launch�����¼�ʵ��
  protected Runnable createRunnableLauncher(RMAppAttempt application, 
      AMLauncherEventType event) {
    Runnable launcher =
        new AMLauncher(context, application, event, getConfig());
    return launcher;
  }
  
  //���Ӧ�������¼�
  private void launch(RMAppAttempt application) {
    Runnable launcher = createRunnableLauncher(application, 
        AMLauncherEventType.LAUNCH);
    //�������¼������¼�������
    masterEvents.add(launcher);
  }
  

  @Override
  protected void serviceStop() throws Exception {
    launcherHandlingThread.interrupt();
    try {
      launcherHandlingThread.join();
    } catch (InterruptedException ie) {
      LOG.info(launcherHandlingThread.getName() + " interrupted during join ", 
          ie);    }
    launcherPool.shutdown();
  }
  
  //ִ���߳�ʵ��
  private class LauncherThread extends Thread {
    
    public LauncherThread() {
      super("ApplicationMaster Launcher");
    }

    @Override
    public void run() {
      while (!this.isInterrupted()) {
        Runnable toLaunch;
        try {
          //ִ�з���Ϊ���¼���������һȡ���¼�
          toLaunch = masterEvents.take();
          //�����̳߳س��н���ִ��
          launcherPool.execute(toLaunch);
        } catch (InterruptedException e) {
          LOG.warn(this.getClass().getName() + " interrupted. Returning.");
          return;
        }
      }
    }
  }    
  
  //���Ӧ��cleanup��ϴ����
  private void cleanup(RMAppAttempt application) {
    Runnable launcher = createRunnableLauncher(application, AMLauncherEventType.CLEANUP);
    //���¼������б���
    masterEvents.add(launcher);
  } 
  
  @Override
  public synchronized void  handle(AMLauncherEvent appEvent) {
    AMLauncherEventType event = appEvent.getType();
    RMAppAttempt application = appEvent.getAppAttempt();
    //��������ApplicationMaster��ȡ�������󣬷�Ϊ�����¼�����ϴ�¼�2��
    switch (event) {
    case LAUNCH:
      launch(application);
      break;
    case CLEANUP:
      cleanup(application);
    default:
      break;
    }
  }
}
