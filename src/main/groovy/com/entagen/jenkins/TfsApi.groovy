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

}
