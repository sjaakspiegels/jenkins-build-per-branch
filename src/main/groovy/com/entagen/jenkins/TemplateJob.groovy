package com.entagen.jenkins

class TemplateJob {
    String jobName
    String baseJobName
    String templateBranchName
    String path

    String jobNameForBranch(String branchName) {
        // git branches often have a forward slash in them, but they make jenkins cranky, turn it into an underscore
        String safeBranchName = branchName.replaceAll('/', '_')
        return "$baseJobName-$safeBranchName"
    }
    
    ConcreteJob concreteJobForBranch(Branch branch) {
        ConcreteJob concreteJob = new ConcreteJob(  templateJob: this, 
                                                    branchName: branch.branchName, 
                                                    jobName: jobNameForBranch(branch.branchName), 
                                                    path: branch.path )
    }
}

