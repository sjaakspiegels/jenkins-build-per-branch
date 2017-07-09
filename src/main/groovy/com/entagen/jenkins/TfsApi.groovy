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
	String tfsUser
	String tfsToken
    Pattern branchNameFilter = null

    public List<String> getBranchNames() {
        println " get branchnames"
 //       String command = "-u $tfsUser:$tfsToken ${tfsUrl}/_apis/tfvc/items?scopePath=${tfsCollection}"

 //       def response = [ 'bash', '-c', "curl ${command}" ].execute().text
        //process.waitFor()
        //println process.err.text
        //println process.text
 //       println response
//def http = new HTTPBuilder('$tfsUrl')
//def html = http.get(path : '/_apis/tfvc/items', query : [scopePath:'$tfsCollection'])
//        String res = doGetHttpRequest(command)
//curl -u svctfsjenkins:agxqquxts22ty2dnh4wvunqvxmzckrd43w236z6p55qwlfvluiwa  http://10.100.10.161:8080/tfs/Voogd/_apis/tfvc/items?scopePath=$/Innovation%20Lab
 //       String command = "git ls-remote --heads ${gitUrl}"
       List<String> branchNames = []

       def list = getAllFolders(tfsCollection)

       list.each { println it}
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
        println command

        def response = [ 'bash', '-c', "curl ${command}" ].execute().text
        println response
        def responseJson = new JsonSlurper().parseText(response)

        println responseJson

        def values = responseJson.value

        values.each { println it }

        values.each {
            if (it.isFolder) {
                def path = URLEncoder.encode(it.path, "UTF-8")
                println path
                branchNames.add(path)
                
                branchNames.add(getAllFolders(path))
            }
        }

        branchNames.each { println it}

        return branchNames
    }

String url = "29th Apartment";  
String encodedUrl = URLEncoder.encode(url, "UTF-8");

    public Boolean passesFilter(String branchName) {
        if (!branchName) return false
        if (!branchNameFilter) return true
        return branchName ==~ branchNameFilter
    }

    // assumes all commands are "safe", if we implement any destructive git commands, we'd want to separate those out for a dry-run
    public void eachResultLine(String command, Closure closure) {
        println "executing command: $command"
        def process = command.execute()
        def inputStream = process.getInputStream()
        def gitOutput = ""

        while(true) {
          int readByte = inputStream.read()
          if (readByte == -1) break // EOF
          byte[] bytes = new byte[1]
          bytes[0] = readByte
          gitOutput = gitOutput.concat(new String(bytes))
        }
        process.waitFor()

        if (process.exitValue() == 0) {
            gitOutput.eachLine { String line ->
               closure(line)
          }
        } else {
            String errorText = process.errorStream.text?.trim()
            println "error executing command: $command"
            println errorText
            throw new Exception("Error executing command: $command -> $errorText")
        }
    }

//    HttpResponse doGetHttpRequest(String requestUrl) {
//        println "RequestUrl: $requestUrl"

//        URL url = new URL(requestUrl)
//        HttpURLConnection connection = url.openConnection()

//        connection.setRequestMethod("GET")

//        HttpResponse resp = new HttpResponse(connection)

//        println "Response: $resp.message"
//        println "Response-body: $resp.body"
        
//        return resp
//    }

}
