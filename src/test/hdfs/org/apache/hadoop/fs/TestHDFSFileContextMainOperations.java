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

package org.apache.hadoop.fs;

import java.io.IOException;
import java.net.URISyntaxException;

import javax.security.auth.login.LoginException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Options.Rename;
import org.apache.hadoop.hdfs.DistributedFileSystem;
import org.apache.hadoop.hdfs.HdfsConfiguration;
import org.apache.hadoop.hdfs.MiniDFSCluster;
import org.apache.hadoop.ipc.RemoteException;
import org.apache.hadoop.hdfs.protocol.FSConstants;
import org.apache.hadoop.security.UnixUserGroupInformation;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class TestHDFSFileContextMainOperations extends
                                  FileContextMainOperationsBaseTest {
  private static MiniDFSCluster cluster;
  private static Path defaultWorkingDirectory;
  
  @BeforeClass
  public static void clusterSetupAtBegining() throws IOException,
      LoginException, URISyntaxException {
    Configuration conf = new HdfsConfiguration();
    cluster = new MiniDFSCluster(conf, 2, true, null);
    fc = FileContext.getFileContext(cluster.getURI(), conf);
    defaultWorkingDirectory = fc.makeQualified( new Path("/user/" + 
        UnixUserGroupInformation.login().getUserName()));
    fc.mkdir(defaultWorkingDirectory, FileContext.DEFAULT_PERM, true);
  }

      
  @AfterClass
  public static void ClusterShutdownAtEnd() throws Exception {
    cluster.shutdown();   
  }
  
  @Before
  public void setUp() throws Exception {
    super.setUp();
  }
  
  @Override
  @After
  public void tearDown() throws Exception {
    super.tearDown();
  }

  @Override
  protected Path getDefaultWorkingDirectory() {
    return defaultWorkingDirectory;
  } 
  
  @Override
   protected IOException unwrapException(IOException e) {
    if (e instanceof RemoteException) {
      return ((RemoteException) e).unwrapRemoteException();
    }
    return e;
  }
  
  @Test
  public void testOldRenameWithQuota() throws Exception {
    DistributedFileSystem fs = (DistributedFileSystem) cluster.getFileSystem();
    Path src1 = getTestRootPath("test/testOldRenameWithQuota/srcdir/src1");
    Path src2 = getTestRootPath("test/testOldRenameWithQuota/srcdir/src2");
    Path dst1 = getTestRootPath("test/testOldRenameWithQuota/dstdir/dst1");
    Path dst2 = getTestRootPath("test/testOldRenameWithQuota/dstdir/dst2");
    createFile(src1);
    createFile(src2);
    fs.setQuota(src1.getParent(), FSConstants.QUOTA_DONT_SET,
        FSConstants.QUOTA_DONT_SET);
    fc.mkdir(dst1.getParent(), FileContext.DEFAULT_PERM, true);

    fs.setQuota(dst1.getParent(), 2, FSConstants.QUOTA_DONT_SET);
    /* 
     * Test1: src does not exceed quota and dst has no quota check and hence 
     * accommodates rename
     */
    oldRename(src1, dst1, true, false);

    /*
     * Test2: src does not exceed quota and dst has *no* quota to accommodate 
     * rename. 
     */
    // dstDir quota = 1 and dst1 already uses it
    oldRename(src2, dst2, false, true);

    /*
     * Test3: src exceeds quota and dst has *no* quota to accommodate rename
     */
    // src1 has no quota to accommodate new rename node
    fs.setQuota(src1.getParent(), 1, FSConstants.QUOTA_DONT_SET);
    oldRename(dst1, src1, false, true);
  }
  
  @Test
  public void testRenameWithQuota() throws Exception {
    DistributedFileSystem fs = (DistributedFileSystem) cluster.getFileSystem();
    Path src1 = getTestRootPath("test/testRenameWithQuota/srcdir/src1");
    Path src2 = getTestRootPath("test/testRenameWithQuota/srcdir/src2");
    Path dst1 = getTestRootPath("test/testRenameWithQuota/dstdir/dst1");
    Path dst2 = getTestRootPath("test/testRenameWithQuota/dstdir/dst2");
    createFile(src1);
    createFile(src2);
    fs.setQuota(src1.getParent(), FSConstants.QUOTA_DONT_SET,
        FSConstants.QUOTA_DONT_SET);
    fc.mkdir(dst1.getParent(), FileContext.DEFAULT_PERM, true);

    fs.setQuota(dst1.getParent(), 2, FSConstants.QUOTA_DONT_SET);
    /* 
     * Test1: src does not exceed quota and dst has no quota check and hence 
     * accommodates rename
     */
    // rename uses dstdir quota=1
    rename(src1, dst1, false, true, false, Rename.NONE);
    // rename reuses dstdir quota=1
    rename(src2, dst1, true, true, false, Rename.OVERWRITE);

    /*
     * Test2: src does not exceed quota and dst has *no* quota to accommodate 
     * rename. 
     */
    // dstDir quota = 1 and dst1 already uses it
    createFile(src2);
    rename(src2, dst2, false, false, true, Rename.NONE);

    /*
     * Test3: src exceeds quota and dst has *no* quota to accommodate rename
     * rename to a destination that does not exist
     */
    // src1 has no quota to accommodate new rename node
    fs.setQuota(src1.getParent(), 1, FSConstants.QUOTA_DONT_SET);
    rename(dst1, src1, false, false, true, Rename.NONE);
    
    /*
     * Test4: src exceeds quota and dst has *no* quota to accommodate rename
     * rename to a destination that exists and quota freed by deletion of dst
     * is same as quota needed by src.
     */
    // src1 has no quota to accommodate new rename node
    fs.setQuota(src1.getParent(), 100, FSConstants.QUOTA_DONT_SET);
    createFile(src1);
    fs.setQuota(src1.getParent(), 1, FSConstants.QUOTA_DONT_SET);
    rename(dst1, src1, true, true, false, Rename.OVERWRITE);
  }
  
  @Test
  public void testRenameRoot() throws Exception {
    Path src = getTestRootPath("test/testRenameRoot/srcdir/src1");
    Path dst = new Path("/");
    createFile(src);
    rename(src, dst, true, false, true, Rename.OVERWRITE);
    rename(dst, src, true, false, true, Rename.OVERWRITE);
  }
  
  private void oldRename(Path src, Path dst, boolean renameSucceeds,
      boolean exception) throws Exception {
    DistributedFileSystem fs = (DistributedFileSystem) cluster.getFileSystem();
    try {
      Assert.assertEquals(renameSucceeds, fs.rename(src, dst));
    } catch (Exception ex) {
      Assert.assertTrue(exception);
    }
    Assert.assertEquals(renameSucceeds, !fc.exists(src));
    Assert.assertEquals(renameSucceeds, fc.exists(dst));
  }
  
  private void rename(Path src, Path dst, boolean dstExists,
      boolean renameSucceeds, boolean exception, Options.Rename... options)
      throws Exception {
    try {
      fc.rename(src, dst, options);
      Assert.assertTrue(renameSucceeds);
    } catch (Exception ex) {
      Assert.assertTrue(exception);
    }
    Assert.assertEquals(renameSucceeds, !fc.exists(src));
    Assert.assertEquals((dstExists||renameSucceeds), fc.exists(dst));
  }
}
