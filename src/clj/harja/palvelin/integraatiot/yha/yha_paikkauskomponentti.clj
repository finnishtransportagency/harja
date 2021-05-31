(ns harja.palvelin.integraatiot.yha.yha-paikkauskomponentti
  (:require [com.stuartsierra.component :as component]
            [hiccup.core :refer [html]]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [harja.palvelin.integraatiot.yha.sanomat
             [paikkauskohteen-lahetyssanoma :as paikkauskohteen-lahetyssanoma]
             [paikkauskohteen-poistosanoma :as paikkauskohteen-poistosanoma]]
            [harja.kyselyt.paikkaus :as q-paikkaus]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.palvelin.tyokalut.ajastettu-tehtava :as ajastettu-tehtava]
            [cheshire.core :as cheshire])
  (:use [slingshot.slingshot :only [throw+ try+]]))

(def +virhe-paikkauskohteen-lahetyksessa+ ::yha-virhe-paikkauskohteen-lahetyksessa)
(def +virhe-paikkauskohteen-poistossa+ ::yha-virhe-paikkauskohteen-poistossa)

(defprotocol PaikkaustietojenLahetys
  (laheta-paikkauskohde [this urakka kohde])
  (laheta-paikkauskohteet-uudelleen [this])
  (poista-paikkauskohde [this urakka kohde])
  (poista-paikkauskohteet-uudelleen [this]))

(defn paivita-lahetyksen-tila
  "Tallentaa lähetettävän tai poistettavan paikkauskohteen tilan eri vaiheissa lähetysprosessia.
  * Odottee vastausta = paikkauskohteen tiedot on lähetetty YHA:aan, mutta YHA ei vielä ole palauttanut vastaussanomaa.
  * Virhe = YHA palautti virheen.
  * Lähetetty = YHA on vastaanottanut lähetys- tai poistosanoman."
  ([db kohde-id tila]
   (paivita-lahetyksen-tila db kohde-id tila nil))
  ([db kohde-id tila virheet]
    (q-paikkaus/paivita-paikkauskohteen-tila db {:harja.domain.paikkaus/id kohde-id
                                                 :harja.domain.paikkaus/yhalahetyksen-tila (name tila)
                                                 :harja.domain.paikkaus/virhe (when virheet
                                                                                (mapv #(:virheviesti %) virheet))})))

(defn kasittele-paikkauskohteen-lahettamisen-vastaus
  "Päivittää virheeseen menneen paikkauskohteen lähteyksen jälkeen paikkauskohteen lähetystilan virheeksi.
   Paikkauskohteen tiedot yritetään lähettää uudelleen YHA:aan ajastetussa tehtävässä."
  ([db kohde-id]
   (kasittele-paikkauskohteen-lahettamisen-vastaus db kohde-id nil))
  ([db kohde-id virheet]
   (let [tila (if virheet :virhe :lahetetty)]
     (paivita-lahetyksen-tila db kohde-id tila virheet)
     (if (= tila :virhe)
       (do
         (log/warn (str "Paikkauskohteen " kohde-id " lähettäminen YHA:an epäonnistui viheeseen " virheet))
         (throw+ {:type    +virhe-paikkauskohteen-lahetyksessa+
                  :virheet {:virhe virheet}}))))))

(defn laheta-paikkauskohde-yhaan [integraatioloki db {:keys [url kayttajatunnus salasana]} urakka-id kohde-id]
  "Lähettää YHA:aan paikkauskohteen kaikki paikkaukset. Sanomaa käytetää uuden paikkauskohteen tietojen lähettämiseen sekä
  olemassa olevan paikauskohteen tietojen päivittämiseen. Päivittäminen poistaa ne paikkaukset, jotka eivät siirry sanomassa.
  YHA:aan lähetetään siis aina kaikki paikkauskohteen paikkaukset."
  (assert (integer? urakka-id) "Urakka-id:n on oltava numero")
  (assert (integer? kohde-id) "Kohde-id:n on oltava numero")

  (try+
    (integraatiotapahtuma/suorita-integraatio
      db integraatioloki "yha" "laheta-paikkauskohde" nil
      (fn [konteksti]
        (paivita-lahetyksen-tila db kohde-id :odottaa_vastausta)
        (let [http-asetukset {:metodi :POST
                              :url (str url "paikkaus/paivitys/")
                              :kayttajatunnus kayttajatunnus
                              :salasana salasana
                              :otsikot {"Content-Type" "application/json"}}
              viestisisalto (paikkauskohteen-lahetyssanoma/muodosta db urakka-id kohde-id)
              {vastaus :body} (integraatiotapahtuma/laheta konteksti :http http-asetukset viestisisalto)
              enkoodattu-body (cheshire/decode vastaus true)]
          (kasittele-paikkauskohteen-lahettamisen-vastaus db kohde-id (:virheet enkoodattu-body)))))
    (catch [:type virheet/+ulkoinen-kasittelyvirhe-koodi+] {:keys [virheet]}
      (kasittele-paikkauskohteen-lahettamisen-vastaus db kohde-id virheet)
      false)))

(defn laheta-paikkauskohteet-yhaan-uudelleen
  "Yrittää lähettää edellisellä kerralla virheeseen päätyneet paikkauskohteet uudelleen YHA:aan."
  [integraatioloki db asetukset]
  (let [hakuehdot {:harja.domain.paikkaus/tila "virhe"}
        lahetettavat-kohteet (q-paikkaus/hae-paikkauskohteet db hakuehdot)]
  (doseq [paikkauskohde lahetettavat-kohteet]
    (laheta-paikkauskohde-yhaan integraatioloki db asetukset
                                (:harja.domain.paikkaus/urakka-id paikkauskohde)
                                (:harja.domain.paikkaus/id paikkauskohde)))))

(defn kasittele-paikkauskohteen-poiston-vastaus
  "Päivittää virheeseen menneen paikkauskohteen poiston jälkeen paikkauskohteen lähetystilan virheeksi.
  Poisto yritetään lähettää uudelleen YHA:aan ajastetussa tehtävässä."
  ([db kohde-id]
   (kasittele-paikkauskohteen-poiston-vastaus db kohde-id nil))
  ([db kohde-id virheet]
   (let [tila (if virheet :virhe :lahetetty)]
     (paivita-lahetyksen-tila db kohde-id tila virheet)
     (if (= tila :virhe)
       (do
         (log/warn (str "Paikkauskohteen " kohde-id " poistaminen YHA:sta epäonnistui. Virhe: " virheet))
         (throw+ {:type    +virhe-paikkauskohteen-poistossa+
                  :virheet {:virhe virheet}}))))))

(defn poista-paikkauskohde-yhasta
  "Lähettää YHA:aan poistosanoman, jolla poistetaan paikkauskohde kokonaisuudesaan.
  YHA:aan lähetetään kohteen kaikkien paikkausten sekä kohteen itsensä harja-id:t.
  Yksittäisen paikkauksen poisto tapahtuu lähettämällä päivitetty paikkauskohde uudelleen YHA:aan.
  Tämä funktio poistaa paikkauskohteen YHA:sta kokonaisuudessaan."
  [integraatioloki db {:keys [url kayttajatunnus salasana]} urakka-id kohde-id]
  ;; Paikkauskohteen ID:llä poistetaan, yksi kohde kerrallaan
  (integraatiotapahtuma/suorita-integraatio
    db integraatioloki "yha" "poista-lahetetty-paikkauskohde" nil
    (fn [konteksti]
      (let [url (str url "paikkaus/poisto/")
            http-asetukset {:metodi         :POST
                            :url            url
                            :kayttajatunnus kayttajatunnus
                            :salasana       salasana
                            :otsikot {"Content-Type" "application/json"}}
            viestisisalto (paikkauskohteen-poistosanoma/muodosta db urakka-id kohde-id)
            {body :body} (integraatiotapahtuma/laheta konteksti :http http-asetukset viestisisalto)
            enkoodattu-body (cheshire/decode body true)]
        (kasittele-paikkauskohteen-poiston-vastaus db kohde-id (:virheet enkoodattu-body))))))

(defn poista-paikkauskohteet-yhasta-uudelleen
  "Yrittää poistaa YHA:sta paikkauskohteet, jotka edellisellä poistokerralla päätyivät virheeseen."
  [integraatioloki db asetukset]
  (let [hakuehdot {:harja.domain.paikkaus/tila "virhe"
                   :harja.domain.muokkaustiedot/poistettu? true}
        poistettavat-kohteet (q-paikkaus/hae-paikkauskohteet db hakuehdot)]
  (doseq [paikkauskohde poistettavat-kohteet]
    (poista-paikkauskohde-yhasta integraatioloki db asetukset
                                 (:harja.domain.paikkaus/urakka-id paikkauskohde)
                                 (:harja.domain.paikkaus/id paikkauskohde)))))

(defn- laheta-tiedot-yhaan-uudelleen! [integraatioloki db asetukset]
  (ajastettu-tehtava/ajasta-paivittain [1 10 0]
                                       (fn [_]
                                         (log/debug "Ajastettu tehtävä käynnistyy: YHA uudelleenlähetykset.")
                                         (laheta-paikkauskohteet-yhaan-uudelleen integraatioloki db asetukset)
                                         (poista-paikkauskohteet-yhasta-uudelleen integraatioloki db asetukset))))


(defrecord YhaPaikkaukset [asetukset]
  component/Lifecycle
  (start [{:keys [integraatioloki db] :as this}]

    (assoc this ::uudelleen-lahetykset-yhaan
                (laheta-tiedot-yhaan-uudelleen! integraatioloki db asetukset)))
  (stop [this]
    ((::uudelleen-lahetykset-yhaan this))
    this)

  PaikkaustietojenLahetys

  (laheta-paikkauskohde [this urakka-id kohde-id]
    (laheta-paikkauskohde-yhaan (:integraatioloki this) (:db this) asetukset urakka-id kohde-id))
  (laheta-paikkauskohteet-uudelleen [this]
    (laheta-paikkauskohteet-yhaan-uudelleen (:integraatioloki this) (:db this) asetukset))
  (poista-paikkauskohde [this urakka-id kohde-id]
    (poista-paikkauskohde-yhasta (:integraatioloki this) (:db this) asetukset urakka-id kohde-id))
  (poista-paikkauskohteet-uudelleen [this]
    (poista-paikkauskohteet-yhasta-uudelleen (:integraatioloki this) (:db this) asetukset)))
