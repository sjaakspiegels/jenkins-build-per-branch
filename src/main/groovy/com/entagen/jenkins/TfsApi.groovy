package com.entagen.jenkins

import java.util.regex.Pattern
import groovyx.net.http.HTTPBuilder
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.DefaultHttpClient

class TfsApi {
    String tfsUrl
    String tfsCollection
	String tfsUser
	String tfsToken
    Pattern branchNameFilter = null

    public List<String> getBranchNames() {
        String command = "-u $tfsUser:$tfsToken ${tfsUrl}/_apis/tfvc/items?scopePath=${tfsCollection}"


//def http = new HTTPBuilder('$tfsUrl')
//def html = http.get(path : '/_apis/tfvc/items', query : [scopePath:'$tfsCollection'])
        String res = doGetHttpRequest(command)
//curl -u svctfsjenkins:agxqquxts22ty2dnh4wvunqvxmzckrd43w236z6p55qwlfvluiwa  http://10.100.10.161:8080/tfs/Voogd/_apis/tfvc/items?scopePath=$/Innovation%20Lab
 //       String command = "git ls-remote --heads ${gitUrl}"
       List<String> branchNames = []

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

    HttpReponse doGetHttpRequest(String requestUrl) {
        println "RequestUrl: $requestUrl"

        URL url = new URL(requestUrl)
        HttpURLConnection connection = url.openConnection()

        connection.setRequestMethod("GET")

        HttpReponse resp = new HttpReponse(connection)

        println "Response: $resp.message"
        println "Response-body: $resp.body"
        
        return resp
    }

}
