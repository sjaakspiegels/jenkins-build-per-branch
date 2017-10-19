package com.entagen.jenkins

import java.util.regex.Pattern
import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.RESTClient
import groovy.json.JsonSlurper
import java.net.URLEncoder
import static groovyx.net.http.ContentType.*
import org.apache.http.conn.HttpHostConnectException
import org.apache.http.client.HttpResponseException
import org.apache.http.HttpStatus
import org.apache.http.HttpRequestInterceptor
import org.apache.http.protocol.HttpContext
import org.apache.http.HttpRequest
import org.apache.http.HttpResponse

class TfsApi {
    String tfsUrl
    String tfsCollection
	String tfsUser
	String tfsToken
    String jenkinsUrl
    String jenkinsUser
    String jenkinsPassword
    Pattern branchNameFilter = null

    public List<String> branchNamesFromProject(String projectName) {

       List<String> branchNames = []

       def list = getAllFolders(tfsCollection)

       branchNames = list.findAll { it.endsWith projectName}

        return branchNames
    }

    public List<String> getAllFolders(String rootFolder) {
        List<String> branchNames = []

        String command = "-u $tfsUser:$tfsToken ${tfsUrl}/_apis/tfvc/items?scopePath=${rootFolder}"

        def response = [ 'bash', '-c', "curl ${command}" ].execute().text
       
        def responseJson = new JsonSlurper().parseText(response)

        def values = responseJson.value

        if (!values.any{elem -> elem.path.endsWith(".sln")}) {

            values.each {
                if (it.isFolder) {
                    
                    def path = URLEncoder.encode(it.path, "UTF-8")
                
                    if (it.path != URLDecoder.decode(rootFolder, "UTF-8")) {
                        branchNames.add(it.path)
                        branchNames.addAll(getAllFolders(path))
                    }
                }
            }
        }

        return branchNames
    }


    public List<String> allBranchNames() {

       List<String> branchNames = []

       return getAllSubFolders(tfsCollection)
    }

    public List<String> getAllSubFolders(String rootFolder) {
        List<String> branchNames = []

        String command = "-u $tfsUser:$tfsToken ${tfsUrl}/_apis/tfvc/items?scopePath=${rootFolder}"

        def response = [ 'bash', '-c', "curl ${command}" ].execute().text
       
        def responseJson = new JsonSlurper().parseText(response)

        def values = responseJson.value

        if (!values.any{elem -> elem.path.endsWith(".sln")}) {

              values.each {
                if (it.isFolder) {
                    
                    def path = URLEncoder.encode(it.path, "UTF-8")
                
                    if (it.path != URLDecoder.decode(rootFolder, "UTF-8")) {
                        if (!it.path.contains("/Dev/")) {
                            branchNames.addAll(getAllSubFolders(path))
                        }
                    }
                }
            }
        }
        else {
            branchNames.add(URLDecoder.decode(rootFolder, "UTF-8"))
        }

        return branchNames
    }

    public CreateServiceHook(ConcreteJob job) {
        def paths = getHookPaths()

        if (!paths.contains(job.path)) {
            println "Creating web hook for job: ${job.jobName} with path ${job.path}"
            
            def jenkinsUserName = jenkinsUser.replace("\\", "\\\\")

            String command = "${tfsUrl}/_apis/hooks/subscriptions?api-version=1.0 -u $tfsUser:$tfsToken " +
                            "-H \"Content-type:application/json\" -X POST -d " + 
                            "' {\"consumerActionId\":\"triggerGenericBuild\", " +
                            "   \"consumerId\":\"jenkins\", " +
                            "   \"eventType\":\"tfvc.checkin\", " +
                            "   \"eventDescription\": \"${job.jobName}\", " +
                            "   \"publisherId\":\"tfs\", " +
                            "   \"scope\":1, " +
                            "   \"consumerInputs\":{ " +
                            "       \"serverBaseUrl\":\"${jenkinsUrl}\", " +
                            "       \"username\":\"${jenkinsUserName}\", " +
                            "       \"password\":\"${jenkinsPassword}\", " +
                            "       \"buildName\":\"${job.jobName}\", " +
                            "       \"useTfsPlugin\":\"built-in\"}, " +
                            "   \"publisherInputs\":{ " +
                            "       \"path\":\"${job.path}\", " +
                            "       \"projectId\":\"9950df28-b8a4-445b-b672-9fc421a628b5\"} " +
                            "   } '" 

            def response = [ 'bash', '-c', "curl ${command}" ].execute().text
        }
    }

    public DeleteServiceHook(String jobName) {
        List<String> buildNames = []

        String command = "-u $tfsUser:$tfsToken ${tfsUrl}/_apis/hooks/subscriptions"

        def response = [ 'bash', '-c', "curl ${command}" ].execute().text
        def responseJson = new JsonSlurper().parseText(response)
        def values = responseJson.value

        for (value in values) {
            if (value.consumerInputs.buildName == jobName) {
                println "Delete ServiceHook " + value.id
                String command = "-X DELETE ${tfsUrl}/_apis/hooks/subscriptions/${value.id}?api-version=1.0 -u $tfsUser:$tfsToken " 
 
                def response = [ 'bash', '-c', "curl ${command}" ].execute().text
            }
        }
    }


    public List<String> getHookPaths() {
        List<String> paths = []

        String command = "-u $tfsUser:$tfsToken ${tfsUrl}/_apis/hooks/subscriptions"

        def response = [ 'bash', '-c', "curl ${command}" ].execute().text
        def responseJson = new JsonSlurper().parseText(response)
        def values = responseJson.value

        for (value in values) {
            if (value.eventType == "tfvc.checkin") {
                paths.add(value.publisherInputs.path)
            }
        }

        return paths
    }


}
