package com.entagen.jenkins

import java.util.regex.Pattern
//import groovyx.net.http.HTTPBuilder
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
    String tfsProject
	String tfsUser
	String tfsToken
    Pattern branchNameFilter = null

    public List<String> getBranchNames() {

       List<String> branchNames = []

       def list = getAllFolders(tfsCollection)

       list.each { println it}

       def list2 = list.findAll { it.endsWith tfsProject}

       List<String> foundBranchNames = []

        list2.each { 
            foundBranchNames.add(it.replaceAll(tfsCollection , ""))
        }
            
        foundBranchNames.each { println it}
   //    def paths = list.path

   //    paths.each { println it }
 //       eachResultLine(command) { String line ->
 //           String branchNameRegex = "^.*\trefs/heads/(.*)\$"
 //           String branchName = line.find(branchNameRegex) { full, branchName -> branchName }
 //           Boolean selected = passesFilter(branchName)
 //           println "\t" + (selected ? "* " : "  ") + "$line"
            // lines are in the format of: <SHA>\trefs/heads/BRANCH_NAME
            // ex: b9c209a2bf1c159168bf6bc2dfa9540da7e8c4a26\trefs/heads/master
 //           if (selected) branchNames << branchName
 //       }

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
                
                    if (path != rootFolder) {
                        branchNames.add(it.path)
                        branchNames.addAll(getAllFolders(path))
                    }
                }
            }
        }

        return branchNames
    }

}
