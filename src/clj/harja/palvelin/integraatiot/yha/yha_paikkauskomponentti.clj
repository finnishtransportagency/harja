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
  (laheta-paikkauskohde [this urakka tunniste])
  (laheta-paikkauskohteet-uudelleen [this])
  (poista-paikkauskohde [this tunniste])
  (poista-paikkauskohteet-uudelleen [this]))

(defn paivita-lahetyksen-tila
  "Tallentaa paikkauskohde-tauluun lähetettävän tai poistettavan paikkauskohteen tilan eri vaiheissa lähetysprosessia.
  Odottee vastausta = paikkauskohteen tiedot on lähetetty YHA:aan, mutta YHA ei vielä ole palauttanut vastaussanomaa.
  Virhe = YHA palautti virheen.
  Lähetetty = YHA on vastaanottanut lähetys- tai poistosanoman."
  ([db kohde-id tila]
   (paivita-lahetyksen-tila db kohde-id tila nil))
  ([db kohde-id tila virheet]
   ;; TODO: Päivitä paikkauskohde-taulun tila ja virhe-sarakkeet id:n perusteella. Käytä toista funktiota, jos tämä ei sovellu.
   ;;(q-paikkaus/paivita-paikkauskohde db nil nil)
   ))

(defn kasittele-paikkauskohteen-lahettamisen-vastaus
  "Päivittää virheeseen menneen paikkauskohteen lähteyksen jälkeen paikkauskohteen lähetystilan virheeksi.
   Paikkauskohteen tiedot yritetään lähettää uudelleen YHA:aan ajastetussa tehtävässä."
  ([db kohde-id]
   (kasittele-paikkauskohteen-lahettamisen-vastaus db kohde-id nil))
  ([db kohde-id virheet]
   (let [tila (if virheet :virhe :lahetetty)]
     (paivita-lahetyksen-tila db kohde-id tila virheet)
     (if (= tila :virhe)
       (log/warn (str "Paikkauskohteen " kohde-id " lähettäminen YHA:an epäonnistui viheeseen " virheet)))))) ;; Aluksi warn-tasolla. Voi laskea debugiin myöhemmin.

;; TODO: rajaa lähetettävät paikkauskohteet toimenpiteen perusteella. Rajaus pitää olla käyttöliittymässäkin, mutta varmista, että ei lähetetä ylimääräistä.
;; TODO: Toimenpiteen rajaus enumeraatiossa?
(defn laheta-paikkauskohde [integraatioloki db {:keys [url kayttajatunnus salasana]} urakka-id kohde-id]
  "Lähettää YHA:aan paikkauskohteen kaikki paikkaukset. Sanomaa käytetää uuden paikkauskohteen tietojen lähettämiseen sekä
  olemassa olevan paikauskohteen tietojen päivittämiseen. Päivittäminen poistaa ne paikkaukset, jotka eivät siirry sanomassa.
  YHA:aan lähetetään siis aina kaikki paikkauskohteen paikkaukset."
  (try+
    (integraatiotapahtuma/suorita-integraatio
      db integraatioloki "yha" "laheta-paikkauskohde" nil
      (fn [konteksti]
        (paivita-lahetyksen-tila db kohde-id :odottaa_vastausta)
        (let [url (str url "paikkauskohde/" kohde-id)       ;; TODO: Selvitä oikea URL YHA:sta
              http-asetukset {:metodi         :POST
                              :url            url
                              :kayttajatunnus kayttajatunnus
                              :salasana       salasana}
              viestisisalto (paikkauskohteen-lahetyssanoma/muodosta db urakka-id kohde-id)
              {vastaus :body} (integraatiotapahtuma/laheta konteksti :http http-asetukset viestisisalto)])))
    (kasittele-paikkauskohteen-lahettamisen-vastaus db kohde-id)
    (catch [:type virheet/+ulkoinen-kasittelyvirhe-koodi+] {:keys [virheet]}
      (kasittele-paikkauskohteen-lahettamisen-vastaus db kohde-id virheet)
      false)))

(defn laheta-paikkauskohteet-uudelleen
  "Yrittää lähettää edellisellä kerralla virheeseen päätyneet paikkauskohteet uudelleen YHA:aan."
  [integraatioloki db asetukset]
  (let [hakuehdot [:tila :virhe] ;; TODO: Tarvitaanko myös aikarajaus?
        uudelleen-lahetettavat-paikkauskohteet (q-paikkaus/hae-paikkaukset-paikkauskohde db hakuehdot)] ;; TODO: hae virheeseen menneet paikkaukset, palauta myös urakan id.
  (doseq [paikkauskohde uudelleen-lahetettavat-paikkauskohteet]
    (laheta-paikkauskohde integraatioloki db asetukset (:urakka-id paikkauskohde) (:id paikkauskohde)))))

(defn kasittele-paikkauskohteen-poiston-vastaus
  "Päivittää virheeseen menneen paikkauskohteen poiston jälkeen paikkauskohteen lähetystilan virheeksi.
  Poisto yritetään lähettää uudelleen YHA:aan ajastetussa tehtävässä."
  [kohde-id virheet ]
  ;; TODO: Onnistunut vai virheellinen. Päivitetäänkö paikkauskohteen lähetys virheeseen, koska siitähän on ultimately kysymys.
  (log/debug (str "Paikkauskohteen " kohde-id " lähettäminen YHA:an epäonnistui viheeseen " virheet)))

;; TODO: kohde kerrallaan vai monta?
(defn poista-paikkauskohde
  "Lähettää YHA:aan poistosanoman, jolla poistetaan paikkauskohde kokonaisuudesaan.
  YHA:aan lähetetään kohteen kaikkien paikkausten sekä kohteen itsensä harja-id:t.
  Yksittäisen paikkauksen poisto tapahtuu lähettämällä päivitetty paikkauskohde uudelleen YHA:aan.
  Tämä funktio poistaa paikkauskohteen YHA:sta kokonaisuudessaan."
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
  (let [hakuehdot [:tila :virhe
                   :poistettu true]
        uudelleen-poistettavat-paikkauskohteet (q-paikkaus/hae-paikkaukset-paikkauskohde db hakuehdot)];; TODO: hae virheeseen menneet paikkaukset
  (doseq [paikkauskohde uudelleen-poistettavat-paikkauskohteet]
    (poista-paikkauskohde integraatioloki db asetukset (:id paikkauskohde)))))

(defrecord YhaPaikkaukset [asetukset]
  component/Lifecycle
  (start [this] this)
  (stop [this] this)

  PaikkaustietojenLahetys

  (laheta-paikkauskohde [this urakka-id kohde-id]
    (laheta-paikkauskohde (:integraatioloki this) (:db this) asetukset urakka-id kohde-id))
  (laheta-paikkauskohteet-uudelleen [this]
    (laheta-paikkauskohteet-uudelleen (:integraatioloki this) (:db this) asetukset))
  (poista-paikkauskohde [this kohde-id]
    (poista-paikkauskohde (:integraatioloki this) (:db this) asetukset kohde-id))
  (poista-paikkauskohteet-uudelleen [this ]
    (poista-paikkauskohteet-uudelleen (:integraatioloki this) (:db this) asetukset)))
