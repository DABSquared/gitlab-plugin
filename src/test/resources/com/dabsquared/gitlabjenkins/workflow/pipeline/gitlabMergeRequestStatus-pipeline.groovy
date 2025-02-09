package com.dabsquared.gitlabjenkins.workflow.pipeline

def mr1 = gitlabMergeRequestStatus('project', 'feature1')
echo "MR1 target = ${mr1.targetBranch}"

def mr2 = gitlabMergeRequestStatus('project', 'feature2')
if (mr2.size() == 0)
  echo 'No MR for feature 2'
