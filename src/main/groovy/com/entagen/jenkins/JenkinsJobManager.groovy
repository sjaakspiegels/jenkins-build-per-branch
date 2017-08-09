package com.entagen.jenkins

import java.util.regex.Pattern

class JenkinsJobManager {
    String templateJobPrefix
    String templateBranchName
    String gitUrl
    String nestedView
    String jenkinsUrl
    String branchNameRegex
    String viewRegex
    String jenkinsUser
    String jenkinsPassword
    String tfsUrl
    String tfsCollection
    String tfsProject
    String tfsUser
    String tfsToken
    
    Boolean dryRun = false
    Boolean noViews = false
    Boolean noDelete = false
    Boolean startOnCreate = false

    JenkinsApi jenkinsApi
	TfsApi tfsApi;

    JenkinsJobManager(Map props) {
        tfsCollection = ""
        for (property in props) {
            this."${property.key}" = property.value
        }

        Map<String, String> env = System.getenv();

        if (env.get("JENKINSUSER") != null) {
            this.jenkinsUser = env.get("JENKINSUSER")
        }
        if (env.get("JENKINSPASSWORD") != null) {
            this.jenkinsPassword = env.get("JENKINSPASSWORD")
        }
        if (env.get("TFSTOKEN") != null) {
            this.tfsToken = env.get("TFSTOKEN")
        }

        initJenkinsApi()
    }

	void syncWithTfs() {

        initTfsApi()
        List<String> allJobNames = jenkinsApi.jobNames

        List<String> projectlist = new ArrayList<String>(Arrays.asList(tfsProject.split(",")));
        println "Projects:"
        projectlist.each { println it}

        projectlist.each {
            println "=== Project: " + it + " ======================="
            String projectTemplateName = templateBranchName + "-" + it
            List<String> allBranchNames = tfsApi.branchNamesFromProject(it)

//            println "=== All branch names ==="
//            allBranchNames.each { println it}

            // ensure that there is at least one job matching the template pattern, collect the set of template jobs
            List<TemplateJob> templateJobs = findRequiredTemplateJobsFromProject(allJobNames, it, projectTemplateName)
 //           println "Template jobs"
 //           templateJobs.each { println it.jobName + "; " + it.baseJobName + "; " + it.templateBranchName}
    
            // create any missing template jobs and delete any jobs matching the template patterns that no longer have branches
            syncJobsTfs(allBranchNames, allJobNames, templateJobs, projectTemplateName, it)

            // create any missing branch views, scoped within a nested view if we were given one
            if (!noViews) {
                syncViews(allBranchNames)
            }
        }
    }

    public void syncJobsTfs(List<String> allBranchNames, List<String> allJobNames, List<TemplateJob> templateJobs, String projectTemplateName, String projectName) {
        List<String> currentTemplateDrivenJobNames = templateDrivenJobNames(templateJobs, allJobNames, projectName)

        List<Branch> tfsBranchPaths = allBranchNames.collect { branchPath -> new Branch( branchName: this.tfsBranchToJobName(branchPath), path: branchPath)}
//        println "branch paths"
//        tfsBranchPaths.each { println it.branchName + "; " + it.path}

//        println "templateBranchName"
//        println templateBranchName

        List<Branch> nonTemplateBranchNames = tfsBranchPaths.findAll { !projectTemplateName.contains(it.branchName)} 
        
        println "nonTemplateBranchNames"
        nonTemplateBranchNames.each { println it.branchName + "; " + it.path}

        List<ConcreteJob> expectedJobs = expectedJobsTfs(templateJobs, nonTemplateBranchNames)

        println "=== CurrentTemplateDrivenJobNames ==="
        currentTemplateDrivenJobNames.each { println it}

        println "=== Expected jobs ==="
        expectedJobs.each { println it.jobName + "; " + it.branchName + "; " + it.path}

        createMissingJobsTfs(expectedJobs, currentTemplateDrivenJobNames, templateJobs)

        println "=== Delete deprecated jobs ==="
        (currentTemplateDrivenJobNames - expectedJobs.jobName).each { println it}

        if (!noDelete) {
            deleteDeprecatedJobs(currentTemplateDrivenJobNames - expectedJobs.jobName)
        }
    }

    public String tfsBranchToJobName(String branchName)
    {
        def name = URLDecoder.decode( branchName, "UTF-8" );
        def collection = URLDecoder.decode( tfsCollection, "UTF-8" );
        
        name = name.replace(collection + "/", "")
        name = name.replace("/", "-")
        
        return name
    }


    public void createMissingJobs(List<ConcreteJob> expectedJobs, List<String> currentJobs, List<TemplateJob> templateJobs) {
        List<ConcreteJob> missingJobs = expectedJobs.findAll { !currentJobs.contains(it.jobName) }
        if (!missingJobs) return

 //       println "Missing jobs"
 //       missingJobs.each { println it.jobName + ": " + it.branchName}


        for(ConcreteJob missingJob in missingJobs) {
            println "Creating missing job: ${missingJob.jobName} from ${missingJob.templateJob.jobName}"
            jenkinsApi.cloneJobForBranch(missingJob, templateJobs)
            if (startOnCreate) {
                jenkinsApi.startJob(missingJob)
            }
        }
    }

    public void createMissingJobsTfs(List<ConcreteJob> expectedJobs, List<String> currentJobs, List<TemplateJob> templateJobs) {
        List<ConcreteJob> missingJobs = expectedJobs.findAll { !currentJobs.contains(it.jobName) }
        if (!missingJobs) return

    //    println "Missing jobs"
    //    missingJobs.each { println it.jobName + ": " + it.branchName}


        for(ConcreteJob missingJob in missingJobs) {
            println "Creating missing job: ${missingJob.jobName} from ${missingJob.templateJob.jobName} with source path ${missingJob.path}"
            jenkinsApi.cloneJobForBranch(missingJob, templateJobs)
            if (startOnCreate) {
                jenkinsApi.startJob(missingJob)
            }
        }
    }

    public void deleteDeprecatedJobs(List<String> deprecatedJobNames) {
        if (!deprecatedJobNames) return
        println "Deleting deprecated jobs:\n\t${deprecatedJobNames.join('\n\t')}"

//        deprecatedJobNames.each { String jobName ->
//            jenkinsApi.deleteJob(jobName)
//        }
    }

    public List<ConcreteJob> expectedJobs(List<TemplateJob> templateJobs, List<String> branchNames) {
        branchNames.collect { String branchName ->
            templateJobs.collect { TemplateJob templateJob -> templateJob.concreteJobForBranch(branchName) }
        }.flatten()
    }

    public List<ConcreteJob> expectedJobsTfs(List<TemplateJob> templateJobs, List<Branch> branches) {
        branches.collect { Branch branch ->
            templateJobs.collect { TemplateJob templateJob -> templateJob.concreteJobForBranch(branch)}
        }.flatten()
    }


    public List<String> templateDrivenJobNames(List<TemplateJob> templateJobs, List<String> allJobNames, String projectName) {
        List<String> templateJobNames = templateJobs.jobName
        List<String> templateBaseJobNames = templateJobs.baseJobName

        // don't want actual template jobs, just the jobs that were created from the templates and project
        return (allJobNames - templateJobNames).findAll { String jobName ->
            templateBaseJobNames.find { String baseJobName -> jobName.startsWith(baseJobName)} && jobName.endsWith(projectName)
        }
    }

    List<TemplateJob> findRequiredTemplateJobsFromProject(List<String> allJobNames, String projectName, String projectTemplateName) {
        String regex = /^($templateJobPrefix-[^-]*)-($projectTemplateName)$/

        List<TemplateJob> templateJobs = allJobNames.findResults { String jobName ->
            TemplateJob templateJob = null
            jobName.find(regex) { full, baseJobName, branchName ->
                templateJob = new TemplateJob(jobName: full, baseJobName: baseJobName, templateBranchName: branchName)
            }
            return templateJob
        }

        assert templateJobs?.size() > 0, "Unable to find any jobs matching template regex: $regex\nYou need at least one job to match the templateJobPrefix and templateBranchName suffix arguments"
        return templateJobs
    }


    public void syncViews(List<String> allBranchNames) {
        List<String> existingViewNames = jenkinsApi.getViewNames(this.nestedView)

//println "Existing ViewNames"
//existingViewNames.each { println it }

        List<BranchView> expectedBranchViews = allBranchNames.collect { String branchName -> new BranchView(branchName: branchName, templateJobPrefix: this.templateJobPrefix) }

//println "Expected ViewNames"
//expectedBranchViews.each { println it.branchName }

        List<BranchView> missingBranchViews = expectedBranchViews.findAll { BranchView branchView -> !existingViewNames.contains(branchView.viewName)}
//println "Missing ViewNames"
//missingBranchViews.each { println it.branchName }

//        addMissingViews(missingBranchViews)

//        if (!noDelete) {
//            List<String> deprecatedViewNames = getDeprecatedViewNames(existingViewNames, expectedBranchViews)
//            deleteDeprecatedViews(deprecatedViewNames)
//        }
    }

    public void addMissingViews(List<BranchView> missingViews) {
        println "Missing views: $missingViews"
        for (BranchView missingView in missingViews) {
            jenkinsApi.createViewForBranch(missingView, this.nestedView, this.viewRegex)
        }
    }

    public List<String> getDeprecatedViewNames(List<String> existingViewNames, List<BranchView> expectedBranchViews) {
         return existingViewNames?.findAll { it.startsWith(this.templateJobPrefix) } - expectedBranchViews?.viewName ?: []
    }

    public void deleteDeprecatedViews(List<String> deprecatedViewNames) {
        println "Deprecated views: $deprecatedViewNames"

        for(String deprecatedViewName in deprecatedViewNames) {
            jenkinsApi.deleteView(deprecatedViewName, this.nestedView)
        }

    }

    JenkinsApi initJenkinsApi() {
        if (!jenkinsApi) {
            assert jenkinsUrl != null
            if (dryRun) {
                println "DRY RUN! Not executing any POST commands to Jenkins, only GET commands"
                this.jenkinsApi = new JenkinsApiReadOnly(jenkinsServerUrl: jenkinsUrl)
            } else {
                this.jenkinsApi = new JenkinsApi(jenkinsServerUrl: jenkinsUrl)
            }

            if (jenkinsUser || jenkinsPassword) this.jenkinsApi.addBasicAuth(jenkinsUser, jenkinsPassword)
        }

        return this.jenkinsApi
    }

	TfsApi initTfsApi() {
		if (!tfsApi) {
			assert tfsUrl != null
			this.tfsApi = new TfsApi(tfsUser: tfsUser, tfsToken: tfsToken, tfsUrl: tfsUrl, tfsCollection: tfsCollection)
            if (this.branchNameRegex){
                this.tfsApi.branchNameFilter = ~this.branchNameRegex
            }
        }

        return this.tfsApi
	}
}