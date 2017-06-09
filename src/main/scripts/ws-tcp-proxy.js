/*
 * Copyright 2016-2017 The Reaktivity Project
 *
 * The Reaktivity Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
var InetAddress = Java.type('java.net.InetAddress');
var HashMap = Java.type('java.util.HashMap');


var headers = new HashMap();
headers.put("upgrade", "websocket");
headers.put(":authority", "localhost:8080");

var port = 8080;
var address = InetAddress.getByName("127.0.0.1");

tcpController.routeClient("ws", 0, "127.0.0.1", 1050, null)
    .thenCompose(function (tcpOutputRef) {
        return wsController.routeServer("http", 0, "tcp", tcpOutputRef, null);
    })
    .thenCompose(function (wsInputRef) {
        return httpController.routeServer("tcp", 0, "ws", wsInputRef, headers);
    })
    .thenCompose(function (httpInputRef) {
        return tcpController.routeServer("any", port, "http", httpInputRef, address);
    })
    .thenAccept(function () {
        print("WS proxy bound to localhost:8080");
    });
