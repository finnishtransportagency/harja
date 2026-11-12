(ns harja.ui.kartta.varit.puhtaat
  (:require [harja.ui.kartta.varit :refer [rgb rgba]]
            [clojure.set :as set]))

(def punainen (rgb 215 103 0))
(def vihrea (rgb 50 203 50))
(def sininen (rgb 39 132 224))
(def violetti (rgb 133 74 160))
(def lime (rgb 184 229 127))
(def pinkki (rgb 199 41 131))
(def musta (rgb 0 0 0))
(def musta-raja (rgb 51 51 51))
(def valkoinen (rgb 255 255 255))
(def harmaa (rgb 140 140 140))
(def tummanharmaa (rgb 77 77 77))

(def syaani (rgb 45 128 176))

;; Näitä värejä käytetään hexoina vektori-ikoneiden värjäämiseen.
;; Värit figmasta.
(def tarkastus-default
  #?(:clj  (rgb 148 167 194)
     :cljs "#94A7C2"))

(def fig-default
  #?(:clj  (rgb 0 176 204)
     :cljs "#00B0CC"))

(def lemon-default
  #?(:clj  (rgb 255 195 0)
     :cljs "#FFC300"))

(def pitaya-default
  #?(:clj  (rgb 229 0 131)
     :cljs "#E50083"))

(def black-light
  #?(:clj  (rgb 92 92 92)
     :cljs "#5C5C5C"))

(def red-default
  #?(:clj  (rgb 222 54 24)
     :cljs "#DE3618"))

;; Kartalla näkyvien elyjen värit, Figmasta
(def tummansininen
  #?(:clj  (rgb 0 114 178)
     :cljs "#0072B2"))

(def vaaleanharmaa
  #?(:clj  (rgb 153 153 153)
     :cljs "#999999"))

(def turkoosi
  #?(:clj  (rgb 86 180 233)
     :cljs "#56B4E9")) ;;"syaani" kanssa nyt sama, asetin syaania hieman tummemmaksi

(def eggplant-default
  #?(:clj  (rgb 38 32 131)
     :cljs "#262083"))

(def magenta
  #?(:clj  (rgb 133 70 135)
     :cljs "#854687")) ;; "violetti" kanssa nyt sama 

(def oranssi
  #?(:clj  (rgb 230 159 0)
     :cljs "#E69F00"))

(def keltainen
  #?(:clj  (rgb 240 228 66)
     :cljs "#F0E442")) ;; "lemon-default" kanssa nyt sama 

(def tummanvihrea
  #?(:clj  (rgb 69 116 92)
     :cljs "#45745C"))

(def pea-default
  #?(:clj  (rgb 26 170 131)
     :cljs "#1AAA83"))

(def elinvoima-varit
  ^{:doc
    (str
      "Elinvoimakeskusten värit, värit kierrätellään kannan ID:n mukaan:"
      "defn- organisaation-geometria")}
  [tummanvihrea vaaleanharmaa keltainen turkoosi
   pea-default magenta tummansininen oranssi eggplant-default])

(def kaikki
  ^{:doc "Vektori joka sisältää kaikki namespacen värit. Joudutaan valitettavasti rakentamaan
          käsin, koska .cljs puolelta puuttuu tarvittavat työkalut tämän luomiseen."
    :const true}
  [punainen oranssi keltainen magenta vihrea
   tarkastus-default tummanvihrea turkoosi tummansininen violetti lime syaani pinkki
   fig-default lemon-default eggplant-default pitaya-default pea-default sininen black-light red-default])

#?(:clj
   (defn- poista-testit [setti]
     (disj setti 'varmenna-sisalto 'varmenna-kaikki-vektori 'elinvoima-varit)))

#?(:clj
   (defn- poista-epavarit [setti]
     (disj setti 'musta 'musta-raja 'valkoinen 'harmaa 'tummanharmaa 'vaaleanharmaa)))

#?(:clj
   (defn varmenna-kaikki-vektori [ns]
     (refer ns :only '[kaikki])
     (let [varit (->
                   (into #{} (keys (ns-publics ns)))
                   (poista-testit)
                   (poista-epavarit)
                   (disj 'kaikki))
           kaikki (count kaikki)]
       (assert
         (= kaikki (count varit))
         (str "\n" ns "/kaikki sisältää " kaikki " väriä, mutta näyttää siltä, että namespacessa on määritelty " (count varit) " väriä. Onko jokin unohtunut lisätä, tai onko namespaceen lisätty esimerkiksi apufunktioita?")))))

#?(:clj
   (defn varmenna-sisalto [ns]
     (varmenna-kaikki-vektori ns)
     (let [core (->
                  (into #{} (keys (ns-publics 'harja.ui.kartta.varit.puhtaat)))
                  (poista-testit))
           verrokki (into #{} (keys (ns-publics ns)))
           puuttuvat (set/difference core verrokki)
           ylimaaraiset (set/difference verrokki core)]

       (assert
         (and
           (empty? puuttuvat) (empty? ylimaaraiset))
         (str
           (when-not (empty? puuttuvat)
             (str "\nNamespacesta " ns " puuttuu määrittely väreille: " (pr-str puuttuvat)))
           (when-not (empty? ylimaaraiset)
             (str "\nNamespacessa " ns " on määritelty värejä jotka tulee lisätä coreen: " (pr-str ylimaaraiset))))))))

#?(:clj (varmenna-kaikki-vektori 'harja.ui.kartta.varit.puhtaat))
