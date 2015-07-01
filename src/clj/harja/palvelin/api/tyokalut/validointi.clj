(ns harja.palvelin.api.tyokalut.validointi
  "Yleisiä API-kutsuihin liittyviä apufunktioita"
  (:require [harja.palvelin.api.tyokalut.virheet :as virheet]
            [harja.kyselyt.urakat :as q]
            [taoensso.timbre :as log]
            [harja.palvelin.oikeudet :as oikeudet]
            [harja.palvelin.palvelut.kayttajat :as kayttajat])
  (:use [slingshot.slingshot :only [throw+ try+]]))

(defn tarkista-urakka [db urakkaid]
  (log/debug "Validoidaan urakkaa id:llä" urakkaid)
  (when (not (q/onko-olemassa? db urakkaid))
    (do
      (log/warn "Urakkaa id:llä " urakkaid " ei löydy.")
      (throw+ {:type    virheet/+sisainen-kasittelyvirhe+
               :virheet [{:koodi  virheet/+tuntematon-urakka-koodi+
                          :viesti (str "Urakkaa id:llä " urakkaid " ei löydy.")}]}))))


(defn tarkista-kayttajan-rooli-urakkaan [urakka-id kayttaja rooli]
  (try+
    (oikeudet/vaadi-rooli-urakassa kayttaja rooli urakka-id)
    (catch RuntimeException e
      (throw+ {:type    virheet/+viallinen-kutsu+
               :virheet [{:koodi  virheet/+kayttajalla-puutteelliset-oikeudet+
                          :viesti (str "Käyttäjällä: " (:kayttajanimi kayttaja) "ei ole roolia:" rooli " urakkaan: " urakka-id)}]}))))

(defn tarkista-lukuoikeus-urakkaan [urakka-id kayttaja]
  (try+
    (oikeudet/vaadi-lukuoikeus-urakkaan kayttaja urakka-id)
    (catch RuntimeException e
      (throw+ {:type    virheet/+viallinen-kutsu+
               :virheet [{:koodi  virheet/+kayttajalla-puutteelliset-oikeudet+
                          :viesti (str "Käyttäjällä lukuoikeutta urakkaan: " urakka-id)}]}))))

(defn tarkista-urakka-ja-kayttaja [db urakka-id kayttaja-id rooli]
  (tarkista-urakka db urakka-id)
  (tarkista-kayttajan-rooli-urakkaan urakka-id kayttaja-id rooli))

(defn tarkista-urakka-ja-lukuoikeus [db urakka-id kayttaja-id]
  (tarkista-urakka db urakka-id)
  (tarkista-lukuoikeus-urakkaan urakka-id kayttaja-id))
