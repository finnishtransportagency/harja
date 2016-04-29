(ns harja.domain.oikeudet
  "Rajapinta oikeustarkistuksiin"
  (:require
   #?(:clj [harja.domain.oikeudet.makrot :refer [maarittele-oikeudet!]])
   [harja.domain.roolit :as roolit]
   #?(:clj [slingshot.slingshot :refer [throw+]])
   #?(:cljs [harja.tiedot.istunto :as istunto]))
  #?(:cljs
     (:require-macros [harja.domain.oikeudet.makrot :refer [maarittele-oikeudet!]])))

(declare on-oikeus? on-muu-oikeus?)
(defrecord KayttoOikeus [kuvaus luku kirjoitus muu])

#?(:cljs
   (extend-type KayttoOikeus
     cljs.core/IFn
     (-invoke
       ([this]
        (or (on-oikeus? :luku this nil @istunto/kayttaja)
            (on-oikeus? :kirjoitus this nil @istunto/kayttaja)))
       ([this urakka-id]
        (or (on-oikeus? :luku this urakka-id @istunto/kayttaja)
            (on-oikeus? :kirjoitus this urakka-id @istunto/kayttaja)))
       ([this urakka-id muu-oikeustyyppi]
        (on-muu-oikeus? muu-oikeustyyppi this urakka-id @istunto/kayttaja)))))

(maarittele-oikeudet!)

(defn on-oikeus?
  "Tarkistaa :luku tai :kirjoitus tyyppisen oikeuden"
  [tyyppi oikeus urakka-id kayttaja]
  (let [sallitut (tyyppi oikeus)]
    (or (roolit/roolissa? kayttaja sallitut)
        (and urakka-id
             (roolit/rooli-urakassa? kayttaja sallitut urakka-id)))))

(defn on-muu-oikeus?
  "Tarkistaa määritellyn muun (kuin :luku tai :kirjoitus) oikeustyypin"
  [tyyppi oikeus urakka-id kayttaja]
  (or (roolit/roolissa? kayttaja roolit/jarjestelmavastaava)
      (let [roolit-joilla-oikeus (get-in oikeus [:muu tyyppi])]
        (and (not (empty? roolit-joilla-oikeus))
             (or (roolit/roolissa? kayttaja roolit-joilla-oikeus)
                 (and urakka-id
                      (roolit/rooli-urakassa? kayttaja roolit-joilla-oikeus urakka-id)))))))
(defn voi-lukea?
  #?(:cljs
     ([oikeus]
      (voi-lukea? oikeus nil @istunto/kayttaja)))
  #?(:cljs
     ([oikeus urakka-id]
      (voi-lukea? oikeus urakka-id @istunto/kayttaja)))
  ([oikeus urakka-id kayttaja]
   (or (roolit/roolissa? kayttaja roolit/jarjestelmavastaava)
       (on-oikeus? :luku oikeus urakka-id kayttaja))))

(defn voi-kirjoittaa?
  #?(:cljs
     ([oikeus]
      (voi-kirjoittaa? oikeus nil @istunto/kayttaja)))
  #?(:cljs
     ([oikeus urakka-id]
      (voi-kirjoittaa? oikeus urakka-id @istunto/kayttaja)))
  ([oikeus urakka-id kayttaja]
   (or (roolit/roolissa? kayttaja roolit/jarjestelmavastaava)
       (on-oikeus? :kirjoitus oikeus urakka-id kayttaja))))

#?(:clj
   (defn lue
     ([oikeus kayttaja]
      (lue oikeus kayttaja nil))
     ([oikeus kayttaja urakka-id]
      (when-not (voi-lukea? oikeus urakka-id kayttaja)
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
      (when-not (voi-kirjoittaa? oikeus urakka-id kayttaja)
        (throw+ (roolit/->EiOikeutta
                 (str "Käyttäjällä '" (:kayttajanimi kayttaja) "' ei kirjoitusoikeutta "
                      (:kuvaus oikeus)
                      (when urakka-id
                        (str " urakassa " urakka-id)))))))))
#?(:clj
   (defn vaadi-oikeus
     ([tyyppi oikeus kayttaja]
      (vaadi-oikeus tyyppi oikeus kayttaja nil))
     ([tyyppi oikeus kayttaja urakka-id]
      (when-not  (on-muu-oikeus? tyyppi oikeus urakka-id kayttaja)
        (throw+ (roolit/->EiOikeutta
                 (str "Käyttäjällä '" (:kayttajanimi kayttaja) "' ei oikeutta '" tyyppi "' "
                      (:kuvaus oikeus)
                      (when urakka-id
                        (str " urakassa " urakka-id)))))))))

(def ilmoitus-ei-oikeutta-muokata-toteumaa
  "Käyttäjäroolillasi ei ole oikeutta muokata tätä toteumaa.")
