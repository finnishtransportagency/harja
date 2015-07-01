(ns harja.palvelin.api.tyokalut.validointi
  "Yleisiä API-kutsuihin liittyviä apufunktioita"
  (:require [harja.palvelin.api.tyokalut.virheet :as virheet]
            [harja.kyselyt.urakat :as q]
            [taoensso.timbre :as log]
            [harja.kyselyt.kayttajat :as kayttajat])
  (:use [slingshot.slingshot :only [throw+]]))

(defn tarkista-urakka [db urakkaid]
  (log/debug "Validoidaan urakkaa id:llä" urakkaid)
  (when (not (q/onko-olemassa? db urakkaid))
    (do
      (log/warn "Urakkaa id:llä " urakkaid " ei löydy.")
      (throw+ {:type    virheet/+sisainen-kasittelyvirhe+
               :virheet [{:koodi  virheet/+tuntematon-urakka-koodi+
                          :viesti (str "Urakkaa id:llä " urakkaid " ei löydy.")}]})))
  ;; todo: lisää luku- ja kirjoitusoikeuksien tarkistukset
  )

(defn hae-kirjaajan-id [db otsikko]
  (let [jarjestelma (:jarjestelma (:lahettaja otsikko))
        ytunnus (:ytunnus (:organisaatio (:lahettaja otsikko)))
        id (:id (first (kayttajat/hae-jarjestelmakayttajan-id-ytunnuksella db jarjestelma ytunnus)))]
    (if id
      id
      (do
        (log/error "Järjestelmäkäyttäjää ei löydy järjestelmälle: " jarjestelma " ja y-tunnukselle:" ytunnus)
        (throw+ {:type    virheet/+sisainen-kasittelyvirhe+
                 :virheet [{:koodi  virheet/+tuntematon-kayttaja-koodi+
                            :viesti (str "Tuntematon järjestelmäkäyttäjä (järjestelmä:" jarjestelma ", y-tunnus:" ytunnus ")")}]})))))