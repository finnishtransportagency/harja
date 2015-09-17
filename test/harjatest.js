
var page = require('webpage').create();

function waitFor(testFx, onReady, onTimeout, until) {
    var untilMs = (until == undefined) ? (Date.now() + 10000) : until;

    if(Date.now() > untilMs) {
	onTimeout();
    } else {
	var condition = page.evaluate(testFx);
	if(condition) {
	    onReady();
	} else {
	    setInterval(function() { waitFor(testFx, onReady, onTimeout, untilMs) },
			250);
	}
    }
    
}



console.log("Testataan latautuuko http://harja-test.solitaservices.fi");

page.open('http://harja-test.solitaservices.fi', function(status) {
    console.log("Sivun lataus: " + status);
    
    if(status === "success") {
	console.log("Odotetaan Harja sovelluksen renderiä");
	
	waitFor(function() { return document.querySelectorAll('img[alt="HARJA"]').length == 1; },
		function() { console.log("Harja ladattu OK"); phantom.exit(0); },
		function() { console.log("Harja latauksessa VIRHE, odotettiin 10s"); phantom.exit(1); });
	
    } else {
	phantom.exit(1);
    }
});
