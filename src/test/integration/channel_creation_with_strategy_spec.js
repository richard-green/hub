require('./integration_config.js');

var request = require('request');
var channelName = utils.randomChannelName();
var channelResource = channelUrl + "/" + channelName;
var testName = __filename;

/**
 * create a channel via put with strategy
 * change the channel using strategy
 */
describe(testName, function () {

    it("puts channel with strategy " + channelName, function (done) {
        request.put({
                url: channelUrl + '/' + channelName,
                headers: {"Content-Type": "application/json"},
                body: {strategy: "BOTH"},
                json: true
            },
            function (err, response, body) {
                expect(err).toBeNull();
                expect(response.statusCode).toBe(201);
                expect(body.strategy).toBe("BOTH");
                done();
            });
    });

    it("updates channel with strategy " + channelName, function (done) {
        request.put({
                url: channelUrl + '/' + channelName,
                headers: {"Content-Type": "application/json"},
                body: {strategy: "BATCH"},
                json: true
            },
            function (err, response, body) {
                expect(err).toBeNull();
                expect(response.statusCode).toBe(201);
                expect(body.strategy).toBe("BATCH");
                done();
            });
    });

});

