(ns harja.palvelin.integraatiot.yha.yha-paikkauskomponentti
  (:require [com.stuartsierra.component :as component]
            [hiccup.core :refer [html]]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [harja.palvelin.integraatiot.yha.sanomat
             [paikkauskohteen-lahetyssanoma :as paikkauskohteen-lahetyssanoma]
             [paikkauskohteen-poistosanoma :as paikkauskohteen-poistosanoma]]
            [harja.pvm :as pvm]
            [harja.kyselyt.konversio :as konv]
            [harja.kyselyt.paikkaus :as q-paikkaus]
            [clojure.string :as clj-str]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet])
  (:use [slingshot.slingshot :only [throw+ try+]]))

(def +virhe-paikkauskohteen-lahetyksessa+ ::yha-virhe-paikkauskohteen-lahetyksessa)
(def +virhe-paikkauskohteen-poistossa+ ::yha-virhe-paikkauskohteen-poistossa)
(def +virhe-paikkausten-poistossa+ ::yha-virhe-paikkauskohteen-viennissa)
(def +virhe-yha-viestin-lukemisessa+ ::yha-virhe-viestin-lukemisessa)

(defprotocol PaikkaustietojenLahetys
  (laheta-paikkauskohde [this tunniste])
  (laheta-paikkauskohteet-uudelleen [this])
  (poista-paikkauskohde [this tunniste])
  (poista-paikkauskohteet-uudelleen [this]))

(defn kasittele-paikkauskohteen-lahettamisen-vastaus
  "Päivittää virheeseen menneen paikkauskohteen lähteyksen jälkeen paikkauskohteen lähetystilan virheeksi.
   Paikkauskohteen tiedot yritetään lähettää uudelleen YHA:aan ajastetussa tehtävässä."
  [kohde-id virheet ]
  (log/debug (str "Paikkauskohteen " kohde-id " lähettäminen YHA:an epäonnistui viheeseen " virheet))

  ;;TODO: Päivitä paikkauskohteen lähetystila: VIRHE jos yhteysvirhe KORJAA jos validointiongelma=

  )

(defn laheta-paikkauskohde [integraatioloki db {:keys [url kayttajatunnus salasana]} kohde-id]
  "Lähettää YHA:aan paikkauskohteen kaikki paikkaukset. Sanomaa käytetää uuden paikkauskohteen tietojen lähettämiseen sekä
  olemassa olevan paikauskohteen tietojen päivittämiseen. Päivittäminen poistaa ne paikkaukset, jotka eivät siirry sanomassa.
  YHA:aan lähetetään aina kaikki paikkauskohteen paikkaukset."
  (try+
    (integraatiotapahtuma/suorita-integraatio
      db integraatioloki "yha" "laheta-paikkauskohde" nil
      (fn [konteksti]
        (let [url (str url "paikkauskohde/" kohde-id)       ;; TODO: Selvitä oikea URL YHA:sta
              http-asetukset {:metodi         :POST
                              :url            url
                              :kayttajatunnus kayttajatunnus
                              :salasana       salasana}
              viestisisalto (paikkauskohteen-lahetyssanoma/muodosta db kohde-id)
              {vastaus :body} (integraatiotapahtuma/laheta konteksti :http http-asetukset viestisisalto)])))
    (catch [:type virheet/+ulkoinen-kasittelyvirhe-koodi+] {:keys [virheet]}
      (kasittele-paikkauskohteen-lahettamisen-vastaus kohde-id virheet)
      false)))

(defn laheta-paikkauskohteet-uudelleen
  "Yrittää lähettää edellisellä kerralla virheeseen päätyneet paikkauskohteet uudelleen YHA:aan."
  [integraatioloki db asetukset]
  (let [hakuehdot []                                        ;; TODO: hakuehdoksi virheeseen menneet paikkauskohteiden lähetykset + jokin aikarajaus + toimenpiderajaus
        uudelleen-lahetettavat-paikkauskohteet (q-paikkaus/hae-paikkaukset-paikkauskohe db hakuehdot)] ;; TODO: hae virheeseen menneet paikkaukset
  (doseq [paikkauskohde uudelleen-lahetettavat-paikkauskohteet]
    (laheta-paikkauskohde integraatioloki db asetukset (:id paikkauskohde)))))


(defn kasittele-paikkauskohteen-poiston-vastaus
  "Päivittää virheeseen menneen paikkauskohteen poiston jälkeen paikkauskohteen lähetystilan virheeksi.
  Poisto yritetään lähettää uudelleen YHA:aan ajastetussa tehtävässä."
  [kohde-id virheet ]
  (log/debug (str "Paikkauskohteen " kohde-id " lähettäminen YHA:an epäonnistui viheeseen " virheet))
  ;;TODO: Päivitä paikkauskohteen lähetystila: VIRHE jos yhteysvirhe KORJAA jos validointiongelma.
  )

;; TODO: kohde kerrallaan vai monta?
(defn poista-paikkauskohde
  "Lähettää YHA:aan poistosanoman, jolla poistetaan paikkauskohde kokonaisuudesaan.
  YHA:aan lähetetään kohteen kaikkien paikkausten sekä kohteen itsensä harja-id:t."
  [integraatioloki db {:keys [url kayttajatunnus salasana]} kohde-id]
  (integraatiotapahtuma/suorita-integraatio
    db integraatioloki "yha" "laheta-paikkauskohde" nil
    (fn [konteksti]
      (let [url (str url "paikkauskohde/" kohde-id) ;; TODO: Selvitä oikea URL YHA:sta
            http-asetukset {:metodi         :DELETE
                            :url            url
                            :kayttajatunnus kayttajatunnus
                            :salasana       salasana}
            viestisisalto (paikkauskohteen-poistosanoma/muodosta db kohde-id)
            {body :body} (integraatiotapahtuma/laheta konteksti :http http-asetukset viestisisalto)]
        (kasittele-paikkauskohteen-poiston-vastaus body kohde-id)))))

(defn poista-paikkauskohteet-uudelleen
  "Yrittää poistaa YHA:sta paikkauskohteet, jotka edellisellä poistokerralla päätyivät virheeseen."
  [integraatioloki db asetukset]
  (let [hakuehdot []                                        ;; TODO: hakuehdoksi virheeseen menneet paikkauskohteiden lähetykset + jokin aikarajaus + toimenpiderajaus
        uudelleen-poistettavat-paikkauskohteet (q-paikkaus/hae-paikkaukset-paikkauskohe db hakuehdot)];; TODO: hae virheeseen menneet paikkaukset
  (doseq [paikkauskohde uudelleen-poistettavat-paikkauskohteet]
    (poista-paikkauskohde integraatioloki db asetukset (:id paikkauskohde)))))


(defrecord YhaPaikkaukset [asetukset]
  component/Lifecycle
  (start [this] this)
  (stop [this] this)

  PaikkaustietojenLahetys

  (laheta-paikkauskohde [this kohde-id]
    (laheta-paikkauskohde (:integraatioloki this) (:db this) asetukset kohde-id))
  (laheta-paikkauskohteet-uudelleen [this]
    (laheta-paikkauskohteet-uudelleen (:integraatioloki this) (:db this) asetukset))
  (poista-paikkauskohde [this kohde-id]
    (poista-paikkauskohde (:integraatioloki this) (:db this) asetukset kohde-id))
  (poista-paikkauskohteet-uudelleen [this ]
    (poista-paikkauskohteet-uudelleen (:integraatioloki this) (:db this) asetukset)))
