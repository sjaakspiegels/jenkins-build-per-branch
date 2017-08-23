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
                        
                        branchNames.addAll(getAllSubFolders(path))
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
        println "Creating web hook for job: ${job.jobName} with path ${job.path}"
    }

    public List<String> getHookPaths() {
        List<String> paths = []

        String command = "-u $tfsUser:$tfsToken ${tfsUrl}/_apis/hooks/subscriptions"

        def response = [ 'bash', '-c', "curl ${command}" ].execute().text
        println response
        def responseJson = new JsonSlurper().parseText(response)
        println responseJson
        paths = responseJson.value.publisherInputs.path

        for (path in paths) {
            println path
        }

        return paths
    }


}
