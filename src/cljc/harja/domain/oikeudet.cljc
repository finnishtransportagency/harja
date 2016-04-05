(ns harja.domain.oikeudet
  "Rajapinta oikeustarkistuksiin"
  (:require
   #?(:clj [harja.domain.oikeudet.makrot :refer [maarittele-oikeudet!]])
   [harja.domain.roolit :as roolit]
   #?(:clj [slingshot.slingshot :refer [throw+]])
   #?(:cljs [harja.tiedot.istunto :as istunto]))
  #?(:cljs
     (:require-macros [harja.domain.oikeudet.makrot :refer [maarittele-oikeudet!]])))

(maarittele-oikeudet!)

(defn sallitut-roolit [tyyppi osio nakyma]
  (get-in oikeudet [osio nakyma tyyppi]))

(def sallitut-luku-roolit (partial sallitut-roolit :luku))
(def sallitut-kirjoitus-roolit (partial sallitut-roolit :kirjoitus))

(defn on-oikeus? [tyyppi osio nakyma urakka-id kayttaja]
  (let [sallitut (sallitut-roolit tyyppi osio nakyma)]
    (or (roolit/roolissa? kayttaja (sallitut-luku-roolit))
        (and urakka-id
             (roolit/rooli-urakassa? kayttaja sallitut urakka-id)))))

(defn voi-lukea?
  #?(:cljs
     ([osio nakyma]
      (voi-lukea? osio nakyma nil istunto/kayttaja)))
  #?(:cljs
     ([osio nakyma urakka-id]
      (voi-lukea osio nakyma urakka-id istunto/kayttaja)))
  ([osio nakyma urakka-id kayttaja]
   (on-oikeus? :luku osio nakyma kayttaja urakka-id)))

(defn voi-kirjoittaa?
  #?(:cljs
     ([osio nakyma]
      (voi-kirjoittaa? osio nakyma nil istunto/kayttaja)))
  #?(:cljs
     ([osio nakyma urakka-id]
      (voi-kirjoittaa? osio nakyma urakka-id istunto/kayttaja)))
  ([osio nakyma urakka-id kayttaja]
   (on-oikeus? :kirjoitus osio nakyma kayttaja urakka-id)))

#?(:clj
   (defn lue
     ([osio nakyma kayttaja]
      (lue osio nakyma kayttaja nil))
     ([osio nakyma kayttaja urakka-id]
      (when-not (voi-lukea? osio nakyma kayttaja urakka-id)
        (throw+ (roolit/->EiOikeutta
                 (str "Käyttäjällä '" (:kayttajanimi kayttaja) "' ei lukuoikeutta "
                      osio " näkymään " nakyma
                      (when urakka-id
                        (str " urakassa " urakka-id)))))))))

#?(:clj
   (defn kirjoita
     ([osio nakyma kayttaja]
      (kirjoita osio nakyma kayttaja nil))
     ([osio nakyma kayttaja urakka-id]
      (when-not (voi-lukea? osio nakyma kayttaja urakka-id)
        (throw+ (roolit/->EiOikeutta
                 (str "Käyttäjällä '" (:kayttajanimi kayttaja) "' ei kirjoitusoikeutta "
                      osio " näkymään " nakyma
                      (when urakka-id
                        (str " urakassa " urakka-id)))))))))
