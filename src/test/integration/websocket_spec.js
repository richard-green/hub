require('./integration_config.js');

var WebSocket = require('ws');

var channelName = utils.randomChannelName();
var channelResource = channelUrl + "/" + channelName;
var testName = __filename;
var request = require('request');

var MINUTE = 60 * 1000;

describe(testName, function () {
    utils.createChannel(channelName);

    var wsUrl = channelResource.replace('http', 'ws') + '/ws'
    console.log('wsUrl ', wsUrl);

    var webSocket;

    it('starts websocket ' + channelName, function (done) {
        webSocket = new WebSocket(wsUrl);
        webSocket.on('open', function (message) {
            console.log('opened wsUrl', wsUrl);
            setTimeout(function () {
                done()
            }, 2000);
        });

    }, 15000);

    utils.addItem(channelResource);

    it('waits for data ' + channelName, function (done) {
        webSocket.onmessage = function (message) {
            console.log('messagedUrl', message.data);
            done();
        };
    }, MINUTE + 1);

    it('closes websocket ' + channelName, function (done) {
        webSocket.onclose = function () {
            console.log('closed ws');
            done();
        };

        webSocket.close();

    }, MINUTE + 2);

});



