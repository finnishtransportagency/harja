(ns harja.ui.kartta.varit.puhtaat
  (:require [harja.ui.kartta.varit :refer [rgb]]
            [clojure.set :as set]))

(def punainen (rgb 255 0 0))
(def oranssi (rgb 255 128 0))
(def keltainen (rgb 255 255 0))
(def magenta (rgb 255 0 255))
(def vihrea (rgb 0 255 0))
(def turkoosi (rgb 0 255 128))
(def syaani (rgb 0 255 255))
(def sininen (rgb 0 128 255))
(def tummansininen (rgb 0 0 255))
(def violetti (rgb 128 0 255))
(def lime (rgb 128 255 0))
(def pinkki (rgb 255 0 128))

(def musta (rgb 0 0 0))
(def valkoinen (rgb 255 255 255))
(def vaaleanharmaa (rgb 242 242 242))
(def harmaa (rgb 140 140 140))
(def tummanharmaa (rgb 77 77 77))

(def kaikki
  ^{:doc "Vektori joka sisältää kaikki namespacen värit. Joudutaan valitettavasti rakentamaan
          käsin, koska .cljs puolelta puuttuu tarvittavat työkalut tämän luomiseen."
    :const true}
  [punainen oranssi keltainen magenta vihrea turkoosi syaani sininen
   tummansininen violetti lime pinkki])

#?(:clj
   (defn- poista-testit [setti]
     (disj setti 'varmenna-sisalto 'varmenna-kaikki-vektori)))

#?(:clj
   (defn- poista-epavarit [setti]
     (disj setti 'musta 'valkoinen 'harmaa 'tummanharmaa 'vaaleanharmaa)))

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
         (str "\n"ns"/kaikki sisältää " kaikki " väriä, mutta näyttää siltä, että namespacessa on määritelty " (count varit) " väriä. Onko jokin unohtunut lisätä, tai onko namespaceen lisätty esimerkiksi apufunktioita?")))))

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
