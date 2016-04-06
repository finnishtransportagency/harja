(ns harja.domain.oikeudet
  "Rajapinta oikeustarkistuksiin"
  (:require
   #?(:clj [harja.domain.oikeudet.makrot :refer [maarittele-oikeudet!]])
   [harja.domain.roolit :as roolit]
   #?(:clj [slingshot.slingshot :refer [throw+]])
   #?(:cljs [harja.tiedot.istunto :as istunto]))
  #?(:cljs
     (:require-macros [harja.domain.oikeudet.makrot :refer [maarittele-oikeudet!]])))

(defrecord KayttoOikeus [kuvaus luku kirjoitus])
(maarittele-oikeudet!)


(defn on-oikeus? [tyyppi oikeus urakka-id kayttaja]
  (let [sallitut (tyyppi oikeus)]
    (or (roolit/roolissa? kayttaja sallitut)
        (and urakka-id
             (roolit/rooli-urakassa? kayttaja sallitut urakka-id)))))

(defn voi-lukea?
  #?(:cljs
     ([oikeus]
      (voi-lukea? oikeus nil @istunto/kayttaja)))
  #?(:cljs
     ([oikeus urakka-id]
      (voi-lukea? oikeus urakka-id @istunto/kayttaja)))
  ([oikeus urakka-id kayttaja]
   (on-oikeus? :luku oikeus urakka-id kayttaja)))

(defn voi-kirjoittaa?
  #?(:cljs
     ([oikeus]
      (voi-kirjoittaa? oikeus nil @istunto/kayttaja)))
  #?(:cljs
     ([oikeus urakka-id]
      (voi-kirjoittaa? oikeus urakka-id @istunto/kayttaja)))
  ([oikeus urakka-id kayttaja]
   (on-oikeus? :kirjoitus oikeus urakka-id kayttaja)))

#?(:clj
   (defn lue
     ([oikeus kayttaja]
      (lue oikeus kayttaja nil))
     ([oikeus kayttaja urakka-id]
      (when-not (voi-lukea? oikeus kayttaja urakka-id)
        (throw+ (roolit/->EiOikeutta
                 (str "Käyttäjällä '" (:kayttajanimi kayttaja) "' ei lukuoikeutta "
                      (:kuvaus oikeus)
                      (when urakka-id
                        (str " urakassa " urakka-id)))))))))

#?(:clj
   (defn kirjoita
     ([oikeus kayttaja]
      (kirjoita oikeus kayttaja nil))
     ([oikeus kayttaja urakka-id]
      (when-not (voi-lukea? oikeus kayttaja urakka-id)
        (throw+ (roolit/->EiOikeutta
                 (str "Käyttäjällä '" (:kayttajanimi kayttaja) "' ei kirjoitusoikeutta "
                      (:kuvaus oikeus)
                      (when urakka-id
                        (str " urakassa " urakka-id)))))))))
