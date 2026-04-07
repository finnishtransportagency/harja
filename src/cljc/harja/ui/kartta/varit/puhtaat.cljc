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
(def fig-default "#00B0CC")
(def lemon-default "#FFC300")
(def pitaya-default "#E50083") ;; "pinkki" kanssa nyt sama 
(def black-light "#5C5C5C")
(def red-default "#DE3618")

;; Kartalla näkyvien elyjen värit, Figmasta
(def tummansininen "#0072B2")
(def vaaleanharmaa "#999999")
(def turkoosi "#56B4E9") ;;"syaani" kanssa nyt sama, asetin syaania hieman tummemmaksi
(def eggplant-default "#262083")
(def magenta "#854687") ;; "violetti" kanssa nyt sama 
(def oranssi "#E69F00")
(def keltainen "#F0E442") ;; "lemon-default" kanssa nyt sama 
(def tummanvihrea "#45745C")
(def pea-default "#1AAA83")

(def elinvoima-varit
  ^{:doc
    (str
      "Elinvoimakeskusten värit, värit kierrätellään kannan ID:n mukaan:"
      "defn- organisaation-geometria") :const true}
  [tummansininen vaaleanharmaa turkoosi
   pea-default magenta oranssi keltainen tummanvihrea eggplant-default])

(def kaikki
  ^{:doc "Vektori joka sisältää kaikki namespacen värit. Joudutaan valitettavasti rakentamaan
          käsin, koska .cljs puolelta puuttuu tarvittavat työkalut tämän luomiseen."
    :const true}
  [punainen oranssi keltainen magenta vihrea
   tummanvihrea turkoosi tummansininen violetti lime syaani pinkki
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
