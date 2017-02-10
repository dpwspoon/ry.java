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

var tcpOutputRef = tcpController.routeOutputNew("ws", 0, "127.0.0.1", 1050, null).get(); 
var wsInputRef = wsController.routeInputNew("http", 0, "tcp", tcpOutputRef, null).get();
var httpInputRef = httpController.routeInputNew("tcp", 0, "ws", wsInputRef, headers).get(); 
var tcpInputRef = tcpController.routeInputNew("any", port, "http", httpInputRef, address).get();

print("WS echo bound to localhost:8080");

