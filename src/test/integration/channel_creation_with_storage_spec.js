require('./integration_config.js');

var request = require('request');
var channelName = utils.randomChannelName();
var channelResource = channelUrl + "/" + channelName;
var testName = __filename;

/**
 * create a channel via put with Storage
 * change the channel using storage
 */
describe(testName, function () {

    it("puts channel with storage " + channelName, function (done) {
        request.put({
                url: channelUrl + '/' + channelName,
                headers: {"Content-Type": "application/json"},
                body: {storage: "BOTH"},
                json: true
            },
            function (err, response, body) {
                expect(err).toBeNull();
                expect(response.statusCode).toBe(201);
                expect(body.storage).toBe("BOTH");
                done();
            });
    });

    it("updates channel with storage " + channelName, function (done) {
        request.put({
                url: channelUrl + '/' + channelName,
                headers: {"Content-Type": "application/json"},
                body: {storage: "BATCH"},
                json: true
            },
            function (err, response, body) {
                expect(err).toBeNull();
                expect(response.statusCode).toBe(201);
                expect(body.storage).toBe("BATCH");
                done();
            });
    });

});

