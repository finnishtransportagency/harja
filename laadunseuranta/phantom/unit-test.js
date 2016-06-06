var system = require('system');
var url,args;

if (phantom.version.major > 1) {
    args = system.args;
    if (args.length < 2) {
        system.stderr.write('Expected a target URL parameter.');
        phantom.exit(1);
    }
    url = args[1];
} else {
    args = phantom.args;
    if (args.length < 1) {
        system.stderr.write('Expected a target URL parameter.');
        phantom.exit(1);
    }
    url = args[0];
}

var page = require('webpage').create();

page.onConsoleMessage = function (message) {
    console.log("CONSOLE: " + message);
};

page.onCallback = function (message) {
    phantom.exit(message.result);
};

page.open(url, function (status) {
    var aja = function() {
	console.log("------ Running tests -------");

	var result = page.evaluate(function() {
	    var result = {
		coords: {
		    latitude: 1,
		    longitude: 2,
		    accuracy: 5,
		    altitude: 10,
		    altitudeAccuracy: 5,
		    heading: 45,
		    speed: 10
		},
		timestamp: 429843
	    };

	    // failaa testit ainakin 10min kuluttua
	    window.setTimeout(function() { window.callPhantom({ result: 1 }); }, 10*60*1000);
	    
	    // mockaa geolocation api
	    navigator.geolocation = {
		watchPosition: function(ok,err,opts) {
		    return setInterval(function() {
			ok(result);
		    }, 1000);
		},
		clearWatch: function(i) {
		    return clearInterval(i);
		},
		getCurrentPosition: function(ok,err,opts) {
		    ok(result);
		}
	    };

	    var printResults = function(results) {
		console.log("Tests    : " + results.test);
		console.log("Passed   : " + results.pass);
		console.log("Fail     : " + results.fail);
		console.log("Errors   : " + results.error);
	    };

	    // aja testit
            harja_laadunseuranta.all_tests.run(function(result) {
		if(result.fail>0 || result.error>0) {
		    printResults(result);
		    console.log("TESTS FAILED!");
		    window.callPhantom({ result: 1 });
		} else {
		    console.log("TESTS SUCCESSFUL!");
		    window.callPhantom({ result: 0 });
		}
	    });
	});
    };
    
    if (status != "success") {
        console.log('Failed to open ' + url);
        phantom.exit(1);
    } else {
	window.setTimeout(aja, 5000);
    }
});
