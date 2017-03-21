(ns harja.palvelin.integraatiot.yha.yha-komponentti
  (:require [com.stuartsierra.component :as component]
            [hiccup.core :refer [html]]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [harja.palvelin.integraatiot.yha.sanomat.urakoiden-hakuvastaussanoma :as urakoiden-hakuvastaus]
            [harja.palvelin.integraatiot.yha.sanomat.urakan-kohdehakuvastaussanoma :as urakan-kohdehakuvastaus]
            [harja.palvelin.integraatiot.yha.sanomat.kohteen-lahetyssanoma :as kohteen-lahetyssanoma]
            [harja.palvelin.integraatiot.yha.sanomat.kohteen-lahetysvastaussanoma :as kohteen-lahetysvastaussanoma]
            [harja.kyselyt.yha :as q-yha-tiedot]
            [harja.kyselyt.paallystys :as q-paallystys]
            [harja.kyselyt.yllapitokohteet :as q-yllapitokohteet]
            [harja.kyselyt.urakat :as q-urakat]
            [harja.pvm :as pvm]
            [harja.kyselyt.konversio :as konv]
            [clojure.string :as string]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet])
  (:use [slingshot.slingshot :only [throw+ try+]]))

(def +virhe-urakoiden-haussa+ ::yha-virhe-urakoiden-haussa)
(def +virhe-urakan-kohdehaussa+ ::yha-virhe-urakan-kohdehaussa)
(def +virhe-kohteen-lahetyksessa+ ::yha-virhe-kohteen-lahetyksessa)

(defprotocol YllapidonUrakoidenHallinta
  (hae-urakat [this yhatunniste sampotunniste vuosi])
  (hae-kohteet [this urakka-id kayttajatunnus])
  (laheta-kohteet [this urakka-id kohde-idt]))

(defn kasittele-urakoiden-hakuvastaus [sisalto otsikot]
  (log/debug format "YHA palautti urakan kohdehaulle vastauksen: sisältö: %s, otsikot: %s" sisalto otsikot)
  (let [vastaus (urakoiden-hakuvastaus/lue-sanoma sisalto)
        urakat (:urakat vastaus)
        virhe (:virhe vastaus)]
    (if virhe
      (do
        (log/error (format "Urakoiden haussa YHA:sta tapahtui virhe: %s" virhe))
        (throw+
          {:type +virhe-urakoiden-haussa+
           :virheet {:virhe virhe}}))
      urakat)))

(defn kasittele-urakan-kohdehakuvastaus [sisalto otsikot]
  (log/debug format "YHA palautti urakoiden haulle vastauksen: sisältö: %s, otsikot: %s" sisalto otsikot)
  (let [vastaus (urakan-kohdehakuvastaus/lue-sanoma sisalto)
        kohteet (:kohteet vastaus)
        virhe (:virhe vastaus)]
    (if virhe
      (do
        (log/error (format "Urakan kohteiden haussa YHA:sta tapahtui virhe: %s" virhe))
        (throw+
          {:type +virhe-urakan-kohdehaussa+
           :virheet {:virhe virhe}}))
      kohteet)))

(defn muodosta-kohteiden-lahetysvirheet [virheet]
  (let [virhe-viestit (string/join ", " (mapv (fn [{:keys [kohde-yha-id selite]}]
                                                (str (when kohde-yha-id (str "Kohde id: " kohde-yha-id ", ")) "Virhe: " selite))
                                              virheet))]
    (str "YHA palautti seuraavat virheet: " virhe-viestit)))

(defn- kasittele-urakan-kohdelahetysvastaus [db sisalto otsikot kohteet]
  (log/debug format "YHA palautti urakan kohteiden kirjauksille vastauksen: sisältö: %s, otsikot: %s" sisalto otsikot)
  (let [vastaus (kohteen-lahetysvastaussanoma/lue-sanoma sisalto)
        virheet (:virheet vastaus)
        onnistunut? (empty? virheet)
        virhe-viesti (muodosta-kohteiden-lahetysvirheet virheet)]
    (if onnistunut?
      (log/info "Kohteiden lähetys YHA:n onnistui")
      (log/error (str "Kohteiden lähetys YHA:n epäonnistui: " virhe-viesti)))

    (doseq [kohde kohteet]
      (let [kohde-id (:id (:kohde kohde))
            kohde-yha-id (:yhaid (:kohde kohde))
            virhe (first (filter #(= kohde-yha-id (:kohde-yha-id %)) (:virheet vastaus)))
            virhe-viesti (:selite virhe)]

        (if onnistunut?
          (q-paallystys/lukitse-paallystysilmoitus! db {:yllapitokohde_id kohde-id})
          (do (log/error (format "Kohteen (id: %s) lähetys epäonnistui. Virhe: \"%s.\"" kohde-id virhe-viesti))
              (q-paallystys/avaa-paallystysilmoituksen-lukko! db {:yllapitokohde_id kohde-id})))

        (q-yllapitokohteet/merkitse-kohteen-lahetystiedot!
          db
          {:lahetetty (pvm/nyt)
           :onnistunut onnistunut?
           :lahetysvirhe virhe-viesti
           :kohdeid kohde-id})))
    onnistunut?))

(defn lisaa-http-parametri [parametrit avain arvo]
  (if arvo
    (assoc parametrit avain arvo)
    parametrit))

(defn hae-kohteen-paallystysilmoitus [db kohde-id]
  (let [ilmoitus (first (q-paallystys/hae-paallystysilmoitus-kohdetietoineen-paallystyskohteella db {:paallystyskohde kohde-id}))]
    (konv/jsonb->clojuremap ilmoitus :ilmoitustiedot)))

(defn hae-alikohteet [db kohde-id paallystys-ilmoitus]
  (let [alikohteet (q-yha-tiedot/hae-yllapitokohteen-kohdeosat db {:yllapitokohde kohde-id})
        osoitteet (get-in paallystys-ilmoitus [:ilmoitustiedot :osoitteet])]
    (mapv (fn [alikohde]

            (let [id (:id alikohde)
                  ilmoitustiedot (first (filter #(= id (:kohdeosa-id %)) osoitteet))]
              (apply merge ilmoitustiedot alikohde)))
          alikohteet)))

(defn hae-kohteen-tiedot [db kohde-id]
  (if-let [kohde (first (q-yllapitokohteet/hae-yllapitokohde db {:id kohde-id}))]
    (let [paallystys-ilmoitus (hae-kohteen-paallystysilmoitus db kohde-id)
          alikohteet (hae-alikohteet db kohde-id paallystys-ilmoitus)]
      {:kohde kohde
       :alikohteet alikohteet
       :paallystys-ilmoitus paallystys-ilmoitus})
    (let [virhe (format "Tuntematon kohde (id: %s)." kohde-id)]
      (log/error virhe)
      (throw+
        {:type +virhe-kohteen-lahetyksessa+
         :virheet {:virhe virhe}}))))

(defn hae-urakat-yhasta [integraatioloki db {:keys [url kayttajatunnus salasana]} yha-nimi sampotunniste vuosi]
  (let [url (str url "urakkahaku")]
    (log/debug (format "Haetaan YHA:sta urakata (tunniste: %s, sampotunnus: %s & vuosi: %s). URL: "
                       yha-nimi sampotunniste vuosi url))
    (integraatiotapahtuma/suorita-integraatio
      db integraatioloki "yha" "urakoiden-haku"
      (fn [konteksti]
        (let [parametrit (-> {}
                             (conj (when (not (empty? yha-nimi)) ["nimi" yha-nimi]))
                             (conj (when (not (empty? sampotunniste)) ["sampo-id" sampotunniste]))
                             (conj (when vuosi ["vuosi" vuosi])))
              otsikot {"Content-Type" "text/xml; charset=utf-8"}
              http-asetukset {:metodi :GET
                              :url url
                              :parametrit parametrit
                              :kayttajatunnus kayttajatunnus
                              :salasana salasana
                              :otsikot otsikot}
              {body :body headers :headers}
              (integraatiotapahtuma/laheta konteksti :http http-asetukset)]
          (kasittele-urakoiden-hakuvastaus body headers))))))

(defn hae-urakan-kohteet-yhasta [integraatioloki db {:keys [url kayttajatunnus salasana]} urakka-id kayttajatunnus]
  (if-let [yha-id (q-yha-tiedot/hae-urakan-yha-id db {:urakkaid urakka-id})]
    (let [url (str url (format "haeUrakanKohteet" yha-id))
          vuosi (pvm/vuosi (pvm/nyt))]
      (log/debug (format "Haetaan urakan (id: %s, YHA-id: %s) kohteet YHA:sta. URL: %s" urakka-id yha-id url))
      (integraatiotapahtuma/suorita-integraatio
        db integraatioloki "yha" "kohteiden-haku"
        (fn [konteksti]
          (let [parametrit (-> {}
                               (lisaa-http-parametri "yha-id" yha-id)
                               (lisaa-http-parametri "vuosi" vuosi)
                               (lisaa-http-parametri "kayttaja" kayttajatunnus))
                http-asetukset {:metodi :GET
                                :url url
                                :parametrit parametrit
                                :kayttajatunnus kayttajatunnus
                                :salasana salasana}
                {body :body headers :headers}
                (integraatiotapahtuma/laheta konteksti :http http-asetukset)]
            (kasittele-urakan-kohdehakuvastaus body headers)))))
    (do
      (let [virhe (format "Urakan (id: %s) YHA-id:tä ei löydy tietokannasta. Kohteita ei voida hakea." urakka-id)]
        (log/error virhe)
        (throw+
          {:type +virhe-urakan-kohdehaussa+
           :virheet {:virhe virhe}})))))

(defn laheta-kohteet-yhaan
  "Lähettää annetut kohteet YHA:an. Mikäli kohteiden lähetys epäonnistuu, niiden päällystysilmoituksen
   lukko avataan, jotta mahdollisesti virheelliset tiedot voidaan korjata. Jos lähetys onnistuu, kohteiden
   päällystysilmoituksen lukitaan.

   Palauttaa true tai false sen mukaan onnistuiko kaikkien kohteiden lähetys."
  [integraatioloki db {:keys [url kayttajatunnus salasana]} urakka-id kohde-idt]
  (log/debug (format "Lähetetään urakan (id: %s) kohteet: %s YHA:n URL:lla: %s." urakka-id kohde-idt url))
  (try+
    (integraatiotapahtuma/suorita-integraatio
      db integraatioloki "yha" "kohteiden-lahetys" nil
      (fn [konteksti]
        (if-let [urakka (first (q-yha-tiedot/hae-urakan-yhatiedot db {:urakka urakka-id}))]
          (let [urakka (assoc urakka :harjaid urakka-id :sampoid (q-urakat/hae-urakan-sampo-id db {:urakka urakka-id}))
                kohteet (mapv #(hae-kohteen-tiedot db %) kohde-idt)
                url (str url "toteumatiedot")
                kutsudata (kohteen-lahetyssanoma/muodosta urakka kohteet)
                otsikot {"Content-Type" "text/xml; charset=utf-8"}
                http-asetukset {:metodi :POST
                                :url url
                                :kayttajatunnus kayttajatunnus
                                :salasana salasana
                                :otsikot otsikot}
                {body :body headers :headers} (integraatiotapahtuma/laheta konteksti :http http-asetukset kutsudata)]
            (kasittele-urakan-kohdelahetysvastaus db body headers kohteet))

          (let [virhe (format "Urakan (id: %s) YHA-tietoja ei löydy." urakka-id)]
            (log/error virhe)
            (throw+
              {:type +virhe-kohteen-lahetyksessa+
               :virheet {:virhe virhe}}))))
      {:virhekasittelija (fn [_ _]
                           (doseq [kohde-id kohde-idt]
                             (q-paallystys/avaa-paallystysilmoituksen-lukko! db {:yllapitokohde_id kohde-id})))})
    (catch [:type virheet/+ulkoinen-kasittelyvirhe-koodi+] {:keys [virheet]}
      false)))

(defrecord Yha [asetukset]
  component/Lifecycle
  (start [this] this)
  (stop [this] this)

  YllapidonUrakoidenHallinta

  (hae-urakat [this yha-nimi sampotunniste vuosi]
    (hae-urakat-yhasta (:integraatioloki this) (:db this) asetukset yha-nimi sampotunniste vuosi))
  (hae-kohteet [this urakka-id kayttajatunnus]
    (hae-urakan-kohteet-yhasta (:integraatioloki this) (:db this) asetukset urakka-id kayttajatunnus))
  (laheta-kohteet [this urakka-id kohde-idt]
    (laheta-kohteet-yhaan (:integraatioloki this) (:db this) asetukset urakka-id kohde-idt)))