/* closure liima, jolla saadaan kätevästi funktio kuvatasoksi */

goog.provide('kuvataso.Lahde');

goog.require('ol.source.Image');

kuvataso.Lahde = function(hae_fn, options) {
    goog.base(this, options);
    this.hae_fn = hae_fn;
}

goog.inherits(kuvataso.Lahde, ol.source.Image);

kuvataso.Lahde.prototype.getImage =
    function(extent, resolution, pixelRatio, projection) {
	var kuva = this.hae_fn(extent, resolution, pixelRatio, projection);
	console.log("kuvataso getImage => " kuva);
	return kuva;
    };
