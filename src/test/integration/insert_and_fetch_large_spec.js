require('./integration_config.js');

var request = require('request');
var http = require('http');
var channelName = utils.randomChannelName();
var channelResource = channelUrl + '/' + channelName;
var testName = __filename;

var MINUTE = 60 * 1000;

/**
 * 1 - create a large payload channel
 * 2 - post a large item (100+ MB)
 * 3 - fetch the item and verify bytes
 */
describe(testName, function () {
    var channelBody = {
        strategy: "LARGE_PAYLOAD",
        tags: ["test"]
    };

    utils.putChannel(channelName, false, channelBody, testName);

    var items = [];

    it("posts a large item to " + channelResource, function (done) {
        request.post({
                url: channelResource,
                headers: {'Content-Type': "text/plain"},
                body: Array(100 * 1024 * 1024).join("a")
            },
            function (err, response, body) {
                expect(err).toBeNull();
                expect(response.statusCode).toBe(201);
                var parse = utils.parseJson(response, testName);
                console.log(response.body);
                console.log(response.headers.location);
                //todo - gfm - get the url
                done();
            });
    }, 5 * MINUTE);

    /*
     it("gets item " + channelResource, function (done) {
     request.get({url: items[1]},
     function (err, response, body) {
     expect(err).toBeNull();
     expect(response.statusCode).toBe(200);
     //todo - gfm - verify body
     /!*expect(response.body).toBe('{ "type" : "coffee", "roast" : "french" }');
     expect(response.headers['content-type']).toBe('application/json');*!/
     done();
     });
     });
     */

});

