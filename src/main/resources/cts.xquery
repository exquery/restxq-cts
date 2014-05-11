(:

  Copyright © 2014, Adam Retter / EXQuery
  All rights reserved.

  Redistribution and use in source and binary forms, with or without
  modification, are permitted provided that the following conditions are met:
      * Redistributions of source code must retain the above copyright
        notice, this list of conditions and the following disclaimer.
      * Redistributions in binary form must reproduce the above copyright
        notice, this list of conditions and the following disclaimer in the
        documentation and/or other materials provided with the distribution.
      * Neither the name of the <organization> nor the
        names of its contributors may be used to endorse or promote products
        derived from this software without specific prior written permission.

  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
  ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
  DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
  DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
  (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
  ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
:)

xquery version "3.0";

module namespace rxcts = "http://exquery.org/ns/restxq/cts";

import module namespace restxq = "http://exquery.org/ns/restxq";
import module namespace http = "http://expath.org/ns/http-client";

declare variable $rxcts:collection := "/db/cts";

declare variable $rxcts:created := 201;
declare variable $rxcts:not-found := 404;
declare variable $rxcts:internal-server-error := 500;

(:~
: RESTXQ Compatibility Test Suite
: Server-side end-point
:
: External CTS uses this to store
: it's XQuerys into the database
:)
declare
    %restxq:PUT("{$body}")
    %restxq:path("/cts/store/{$name}")
    %restxq:consumes("application/xquery")
    %restxq:produces("application/xml")
function rxcts:store-query($name, $body) as document-node(element(restxq:response)) {
    let $path :=
        if(not(rxcts:collection-available(rxcts:collection)))then
            rxcts:create-collection($rxcts:collection)
        else
            $rxcts:collection
    return
        let $stored := rxcts:store($path, $body)
        return
            let $response :=
                if(empty($stored))then
                    $rxcts:internal-server-error
                else
                    $rxcts:created
            return
                <restxq:response>
                    <http:response status="{$response}"/>
                </restxq:response>
};

declare
    %restxq:DELETE
    %restxq:path("/cts/delete/{$name}")
    %restxq:consumes("application/xquery")
    %restxq:produces("application/xml")
function rxcts:remove-query($name) as document-node(element(restxq:response)) {
    let $path := $rxcts:collection || "/" || $name
    return
        let $response :=
            if(doc-available($path))then
                if(rxcts:remove($path))then
                    $rxcts:no-content
                else
                    $rxcts:internal-server-error
            else
                $rxcts:not-found
        return
            <restxq:response>
                <http:response status="{$response}"/>
            </restxq:response>
};


(: Implementation specifics below... :)

declare
    %private
function rxcts:create-collection($path as xs:string) as xs:string {
    let $parts := tokenize($path, "/")
    return
        rxcts:create-collection($parts[1], subsequence($parts, 2))[1]
};

declare
    %private
function rxcts:create-collection($root as xs:string, $paths as xs:string+) as xs:string* {
    if(empty($paths))then
        $root
    else
        let $current := $root || "/" || $paths[1]
        return
            (
                if(not(rxcts:collection-available($current)))then
                    xmldb:create-collection($root, $paths[1])
                else(),
                rxcts:create-collection($current, subsequence($parts, 2))
            )
};

declare
    %private
function rxcts:collection-available($uri) {
    rxcts:collection-available($uri)
};

declare
    %private
function rxcts:store($path, $body) as xs:string? {
    let $coll := replace($path, "(.*)/", "$1")
    let $res := replace($path, "(.*/).*", "")
    return
        let $stored := xmldb:store($coll, $res, $body, "application/octet-stream")
        return
            let $null := sm:chmod(xs:anyURI($stored), "rwxrwxrwx")
            return
                $stored
};

declare
    %private
function rxcts:remove($path) as xs:boolean {
    let $coll := replace($path, "(.*)/", "$1")
    let $res := replace($path, "(.*/).*", "")
    return
        xmldb:remove($coll, $res)
};