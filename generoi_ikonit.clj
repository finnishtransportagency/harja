#!/usr/local/bin/planck
(ns harja.generoi-ikonit
    (:require
      [planck.core :as planck]
      [planck.shell :as shell]
      [clojure.string :as string])
    (:require-macros [planck.shell :as shell]))

;; Viralliset värit löytyy täältä: https://issues.solita.fi/browse/HAR-921
;; Testin vuoksi voit muuttaa pelkkää värin arvoa, mutta pyritään noudattamaan
;; virallista listaa.
;; Jos lisäät uuden värin, lisää se myös puhtaat.cljc ja alpha.cljc
(def varit {"punainen"      "rgb(255,0,0)"
            "oranssi"       "rgb(255,128,0)"
            "keltainen"     "rgb(255,255,0)"
            "lime"          "rgb(128,255,0)"
            "vihrea"        "rgb(0,255,0)"
            "turkoosi"      "rgb(0,255,128)"
            "syaani"        "rgb(0,255,255)"
            "sininen"       "rgb(0,128,255)"
            "tummansininen" "rgb(0,0,255)"
            "violetti"      "rgb(128,0,255)"
            "magenta"       "rgb(255,0,255)"
            "pinkki"        "rgb(255,0,128)"


            ;; Epävärit ;)
            "musta"         "rgb(0,0,0)"
            "vaaleanharmaa" "rgb(242,242,242)"
            "harmaa"        "rgb(140,140,140)"
            "tummanharmaa"  "rgb(77,77,77)"})

(defn style [vari] (str "style=\"fill:" vari ";\""))

(def kansio "resources/public/images/tuplarajat/")
(def nuolikansio (str kansio "nuolet/"))
(def sijaintikansio (str kansio "sijainnit/"))
(def pinnikansio (str kansio "pinnit/"))

(defn sijaintitiedosto [ulkovari sisavari] (str sijaintikansio "sijainti-" ulkovari "-" sisavari ".svg"))
(defn nuolitiedosto [vari] (str nuolikansio "nuoli-" vari ".svg"))
(defn pinnitiedosto [vari] (str pinnikansio "pinni-" vari ".svg"))


;; Nämä sisällöt on kopioutu "tuplarajallisista" ikoneista, eli esim sijaintiikonissa on ulkoreunassa,
;; ja sisällä olevan pallon reunassa, musta viiva. HARJAan on piirretty ikonit joissa musta viiva on vain
;; ulkoreunassa, ja ikonit joissa mustaa viivaa ei ole ollenkaan, mutta tuplaraja on todettu parhaaksi.
;; Hyvin epätodennäköistä että tämä asia tulee muuttumaan, mutta jos muuttuu, niin nämä kolme
;; täytyy määritellä uusiksi.
(defn sijainti-ikoni [ulkovari sisavari]
      (str "<?xml version=\"1.0\" encoding=\"utf-8\"?>
<!-- Generator: Adobe Illustrator 19.2.1, SVG Export Plug-In . SVG Version: 6.00 Build 0)  -->
<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" \"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">
<svg version=\"1.1\" id=\"Layer_1\" xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" x=\"0px\" y=\"0px\"
width=\"24px\" height=\"24px\" viewBox=\"0 0 24 24\" style=\"enable-background:new 0 0 24 24;\" xml:space=\"preserve\">
<path " (style ulkovari) " d=\"M12,0.5C7.9,0.5,4.5,3.9,4.5,8c0,1,0.2,2,0.6,2.9L12,23l6.9-12.1C19.3,10,19.5,9,19.5,8
	C19.5,3.9,16.1,0.5,12,0.5z\"/>
<path d=\"M12,1c3.9,0,7,3.1,7,7c0,0.9-0.2,1.8-0.5,2.7L12,22L5.5,10.7C5.2,9.8,5,8.9,5,8C5,4.1,8.1,1,12,1 M12,0C7.6,0,4,3.6,4,8
	c0,1.1,0.2,2.2,0.6,3.1L12,24l7.4-12.9c0.4-1,0.6-2,0.6-3.1C20,3.6,16.4,0,12,0L12,0z\"/>
<circle "
           (style sisavari) " cx=\"12\" cy=\"8\" r=\"4.5\"/>
<path d=\"M12,4c2.2,0,4,1.8,4,4s-1.8,4-4,4s-4-1.8-4-4S9.8,4,12,4 M12,3C9.2,3,7,5.2,7,8s2.2,5,5,5s5-2.2,5-5S14.8,3,12,3L12,3z\"/>
</svg>"))

(defn pinni-ikoni [vari]
      (str "<?xml version=\"1.0\" encoding=\"utf-8\"?>
  <!-- Generator: Adobe Illustrator 19.2.1, SVG Export Plug-In . SVG Version: 6.00 Build 0)  -->
<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" \"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">
<svg version=\"1.1\" id=\"Layer_1\" xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" x=\"0px\" y=\"0px\"
width=\"24px\" height=\"24px\" viewBox=\"0 0 24 24\" style=\"enable-background:new 0 0 24 24;\" xml:space=\"preserve\">
<path " (style vari) " d=\"M18.5,7c0-3.6-2.9-6.5-6.5-6.5S5.5,3.4,5.5,7c0,2.9,2,5.5,4.8,6.3l0.3,0.1l1.4,7.8l1.4-7.8l0.3-0.1
	C16.5,12.5,18.5,9.9,18.5,7z\"/>
<path d=\"M12,1c3.3,0,6,2.7,6,6c0,2.7-1.8,5.1-4.4,5.8L13,12.9l-0.1,0.6L12,18.4l-0.9-4.8L11,12.9l-0.6-0.2C7.8,12.1,6,9.7,6,7
	C6,3.7,8.7,1,12,1 M12,0C8.1,0,5,3.1,5,7c0,3.2,2.2,5.9,5.2,6.7L12,24l1.8-10.3c3-0.8,5.2-3.5,5.2-6.7C19,3.1,15.9,0,12,0L12,0z\"/>
</svg>"))

(defn nuoli-ikoni [vari]
      (str "<?xml version=\"1.0\" encoding=\"utf-8\"?>
  <!-- Generator: Adobe Illustrator 19.2.1, SVG Export Plug-In . SVG Version: 6.00 Build 0)  -->
<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" \"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">
<svg version=\"1.1\" id=\"Layer_1\" xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" x=\"0px\" y=\"0px\"
width=\"24px\" height=\"24px\" viewBox=\"0 0 24 24\" style=\"enable-background:new 0 0 24 24;\" xml:space=\"preserve\">
<polygon " (style vari) " points=\"7.8,1.4 5.6,3.5 14.1,12 5.6,20.5 7.8,22.6 18.4,12 \"/>
<path d=\"M17.7,12l-9.9,9.9l-1.4-1.4l8.5-8.5L6.3,3.5l1.4-1.4 M7.8,0.7L4.9,3.5l8.5,8.5l-8.5,8.5l2.8,2.8L19.1,12L7.8,0.7L7.8,0.7z\"/>
</svg>"))

(println "\n***************************")
(println "Generoidaan nuolet kansioon: " nuolikansio)
(doall
  (for [[vari rgb] varit]
       #_(println "Generoidaan " (nuolitiedosto vari))
       (planck/spit (nuolitiedosto vari) (nuoli-ikoni rgb))))

(println "\n***************************")
(println "Generoidaan pinnit kansioon: " pinnikansio)
(doall
  (for [[vari rgb] varit]
       #_(println "Generoidaan " (pinnitiedosto vari))
       (planck/spit (pinnitiedosto vari) (pinni-ikoni rgb))))

(println "\n***************************")
(println "Generoidaan sijainnit kansioon: " sijaintikansio)
(doall
  (for [[ulkovari ulkorgb] varit
        [sisavari sisargb] varit]
       #_(println "Generoidaan " (sijaintitiedosto ulkovari sisavari))
       (planck/spit (sijaintitiedosto ulkovari sisavari) (sijainti-ikoni ulkorgb sisargb))))

(def kansiot (map #(shell/with-sh-dir % (apply str (drop-last (:out (shell/sh "pwd"))))) [sijaintikansio nuolikansio pinnikansio]))

(println "\n\n Valmis! Generoi vielä lopuksi .png kuvat IE:n takia. Vaatii fish shellin")

(defn komento-kansiolle [kansio]
      (str "cd " kansio "; and for i in *.svg; /Applications/Inkscape.app/Contents/Resources/script --without-gui --export-png=(pwd)/(echo $i | sed 's/\\.[^.]*$//').png (pwd)/$i; end; "))

(def komento (string/join "; and " (mapv komento-kansiolle kansiot)))

(println "******* AJA TÄMÄ KOMENTO *******")
(println komento)
(println "********************************")

;(def pwd (apply str (drop-last (:out (shell/sh "pwd")))))
;(def script (str pwd "/svg-to-png.fish"))

;(println "Generoidaan .svg kuvista .png kuvat IE:tä varten skriptillä " script)
;(println "Vaatii fish shellin!")

#_(doall (for [kansio [sijaintikansio nuolikansio pinnikansio]]
            (println "Konversio kansiossa " kansio)
            (shell/with-sh-dir kansio (shell/sh "fish" script))))