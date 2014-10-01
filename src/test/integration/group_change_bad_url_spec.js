require('./integration_config.js');

var request = require('request');
var http = require('http');
var channelName = utils.randomChannelName();
var groupName = utils.randomChannelName();
var channelResource = channelUrl + "/" + channelName;
var testName = __filename;
;



/**
 * This should:
 *
 * 1 - create a channel
 * 2 - create a group on that channel with a non-existent endpointA
 * 3 - post item into the channel
 * 4 - delete the group
 * 5 - create the group with the same name and a new endpointB
 * 6 - start a server at the endpointB
 * 7 - post item - should see item at endPointB
 */

describe(testName, function () {

    var portB = callbackPort + 5;

    var itemsB = [];
    var badConfig = {
        callbackUrl : 'http://hub.svc.dev/austrianairlinesParser',
        channelUrl : channelResource
    };
    var groupConfigB = {
        callbackUrl : callbackDomain + ':' + portB + '/',
        channelUrl : channelResource
    };

    utils.createChannel(channelName);

    utils.putGroup(groupName, badConfig);

    utils.addItem(channelResource);

    utils.deleteGroup(groupName);

    utils.putGroup(groupName, groupConfigB);

    it('runs callback server', function () {
        utils.startServer(portB, function (string) {
            itemsB.push(string);
        });

        runs(function () {
            utils.postItem(channelResource);
        });

        waitsFor(function () {
            return itemsB.length == 1;
        }, 12000);

    });

    utils.closeServer(function () {
        expect(JSON.parse(itemsB[0]).uris[0]).toBe(channelResource + '/1001');
        expect(itemsB.length).toBe(1);
    });
});
