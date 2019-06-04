var system = require('system');
var page = require('webpage').create();
page.viewportSize = {
  width: 1024,
  height: 768
};

page.onConsoleMessage = function(msg, lineNum, sourceId) {
    console.log('CONSOLE: ' + msg + ' (from line #' + lineNum + ':' + sourceId + ')');
};

page.onResourceRequested = function(request) {
  console.log('Request: ' + request.url);
};

page.onResourceReceived = function(response) {
  console.log('Receive: ' + request.statusText + ' ' + request.url);
};

if(system.args.length<3) {
    console.log("usage: phantomjs phantom_tilannekuva.js <username> <password>");
    phantom.exit(1);
}

var username = system.args[1];
var password = system.args[2];

var jquery_url = 'https://ajax.googleapis.com/ajax/libs/jquery/1.6.1/jquery.min.js';

page.open('https://testiextranet.vayla.fi/harja/#tilannekuva?', function(status) {
    if(status === "success") {
	page.includeJs(jquery_url, function() {
	    page.onLoadFinished = function(status) {
		page.includeJs(jquery_url, function() {
		    page.evaluate(function() {
			setTimeout(function() {
			    $('#sivut > li:nth-child(3) > a')[0].click();
			    setTimeout(function() {
				$('#tk-suodattimet > div:nth-child(2) > div > div > div > div > div.harja-checkbox-laatikko').click();
				$('#tk-suodattimet > div:nth-child(3) > div > div > div > div > div.harja-checkbox-laatikko').click();
				$('#tk-paavalikko > div.tk-suodatinryhmat > div > div:nth-child(1) > div > div > div > div > div.harja-checkbox-laatikko').click();
				$('#tk-paavalikko > div.tk-suodatinryhmat > div > div:nth-child(2) > div > div > div > div > div.harja-checkbox-laatikko').click();
				$('#tk-paavalikko > div.tk-suodatinryhmat > div > div:nth-child(3) > div > div > div > div > div.harja-checkbox-laatikko').click();
				$('#tk-paavalikko > div.tk-suodatinryhmat > div > div:nth-child(4) > div > div > div > div > div.harja-checkbox-laatikko').click();
			    }, 2000);
			}, 10000);
		    });
		});
	    };
	    page.evaluate(function(username, password) {
		$('#username').val(username);
		$('#password').val(password);
		$('input[type=submit]').click();
	    }, username, password);
	});
    } else {
	console.log("Sivua ei voitu avata!");
    }
});
