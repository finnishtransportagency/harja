(ns harja.palvelin.integraatiot.yha.yha-komponentti
  (:require [com.stuartsierra.component :as component]
            [hiccup.core :refer [html]]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [harja.palvelin.integraatiot.yha.sanomat
             [urakoiden-hakuvastaussanoma :as urakoiden-hakuvastaus]
             [urakan-kohdehakuvastaussanoma :as urakan-kohdehakuvastaus]
             [kohteen-lahetyssanoma :as kohteen-lahetyssanoma]
             [kohteen-lahetysvastaussanoma :as kohteen-lahetysvastaussanoma]
             [kohteen-poistovastaussanoma :as kohteen-poistovastaussanoma]]
            [harja.kyselyt.yha :as q-yha-tiedot]
            [harja.kyselyt.paallystys :as q-paallystys]
            [harja.kyselyt.yllapitokohteet :as q-yllapitokohteet]
            [harja.pvm :as pvm]
            [harja.kyselyt.konversio :as konv]
            [clojure.string :as clj-str]
            [clojure.java.jdbc :as jdbc]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.palvelin.palvelut.yllapitokohteet.maaramuutokset :as maaramuutokset])
  (:use [slingshot.slingshot :only [throw+ try+]]))

(def +virhe-urakoiden-haussa+ ::yha-virhe-urakoiden-haussa)
(def +virhe-urakan-kohdehaussa+ ::yha-virhe-urakan-kohdehaussa)
(def +virhe-kohteen-lahetyksessa+ ::yha-virhe-kohteen-lahetyksessa)
(def +virhe-kohteen-poistamisessa+ ::yha-virhe-kohteen-poistamisessa)
(def +virhe-yha-viestin-lukemisessa+ ::yha-virhe-viestin-lukemisessa)

(defprotocol YllapidonUrakoidenHallinta
  (hae-urakat [this yhatunniste sampotunniste vuosi])
  (hae-kohteet [this urakka-id kayttajatunnus])
  (laheta-kohteet [this urakka-id kohde-idt])
  (poista-kohde [this kohde-id]))

(defn kasittele-urakoiden-hakuvastaus [sisalto otsikot]
  (log/debug (format "YHA palautti urakan kohdehaulle vastauksen: sisältö: %s, otsikot: %s" sisalto otsikot))
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
  (log/debug (format "YHA palautti urakoiden haulle vastauksen: sisältö: %s, otsikot: %s" sisalto otsikot))
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

(defn kasittele-kohteen-poistamisen-vastaus
  [body yha-kohde-id]
  (let [{:keys [onnistunut? virheet sanoman-lukuvirhe?]} (try (kohteen-poistovastaussanoma/lue-sanoma body)
                                                              (catch RuntimeException e
                                                                {:virheet            [{:selite       "YHA:sta saatua viestiä ei voitu lukea"
                                                                                       :kohde-yha-id yha-kohde-id}]
                                                                 :sanoman-lukuvirhe? true}))]
    (when-not onnistunut?
      (log/error (str "Kohteen (" yha-kohde-id ") poistaminen YHA:sta epäonnistui: " virheet))
      (if sanoman-lukuvirhe?
        (throw+ {:type    +virhe-yha-viestin-lukemisessa+
                 :virheet virheet})
        (throw+ {:type    +virhe-kohteen-poistamisessa+
                 :virheet virheet})))))

(defn muodosta-kohteiden-lahetysvirheet [virheet virheellisen-kohteen-tiedot]
  (mapv (fn [{:keys [kohde-yha-id selite]}]
          (str (when kohde-yha-id
                 (let [{:keys [nimi tunnus kohdenumero]} (some #(when (= (:yhaid %) kohde-yha-id)
                                                      %)
                                                   virheellisen-kohteen-tiedot)]
                   (str "Kohde id: " kohde-yha-id
                        (when kohdenumero
                          (str ", kohdenumero: " kohdenumero))
                        (when tunnus
                          (str ", tunnus: " tunnus))
                        (when nimi
                          (str ", nimi: " nimi))
                        ", ")))
               "Virhe: " selite))
        virheet))

(defn- kasittele-urakan-kohdelahetysvastaus [db sisalto otsikot kohteet]
  (log/debug format "YHA palautti urakan kohteiden kirjauksille vastauksen: sisältö: %s, otsikot: %s" sisalto otsikot)
  (jdbc/with-db-transaction [db db]
    (let [vastaus (try (kohteen-lahetysvastaussanoma/lue-sanoma sisalto)
                       (catch RuntimeException e
                         {:virheet [{:selite (.getMessage e)}]
                          :sanoman-lukuvirhe? true}))
          virheet (:virheet vastaus)
          onnistunut? (empty? virheet)
          virheellisen-kohteen-tiedot (when-not onnistunut?
                                        (q-paallystys/virheen-tiedot db {:ulkoiset-idt (map :kohde-yha-id virheet)}))
          virhe-viestit (muodosta-kohteiden-lahetysvirheet virheet virheellisen-kohteen-tiedot)
          virhe-viesti (str "YHA palautti seuraavat virheet: " (clj-str/join ", " virhe-viestit))
          epaonnistuneet (into #{}
                               ;; Jos on yksikään virhe, jolla ei ole kohde id:tä
                               ;; katsotaan kaikki kohteet epäonnistuneiksi.
                               (if (some #(nil? (:kohde-yha-id %)) virheet)
                                 (map #(:yhaid (:kohde %)) kohteet)
                                 (map :kohde-yha-id virheet)))]

      (if onnistunut?
        (log/info "Kohteiden lähetys YHAan onnistui")
        (log/error (str "Virheitä kohteiden lähetyksessä YHAan: " virhe-viesti)))

      (doseq [kohde kohteet]
        (let [kohde-id (:id (:kohde kohde))
              kohde-yha-id (:yhaid (:kohde kohde))
              kohteen-lahetys-onnistunut? (and (not (contains? epaonnistuneet kohde-yha-id))
                                               (not (:sanoman-lukuvirhe? vastaus)))
              virhe (first (filter #(= kohde-yha-id (:kohde-yha-id %)) virheet))
              virhe-viesti (when (not kohteen-lahetys-onnistunut?)
                             (or (:selite virhe)
                                 (clj-str/join ", " (map :selite (filter #(nil? (:kohde-yha-id %)) virheet)))))]

          (if kohteen-lahetys-onnistunut?
            (q-paallystys/lukitse-paallystysilmoitus! db {:yllapitokohde_id kohde-id})
            (do (log/error (format "Kohteen (id: %s) lähetys epäonnistui. Virhe: \"%s\"" kohde-id virhe-viesti))
                (q-paallystys/avaa-paallystysilmoituksen-lukko! db {:yllapitokohde_id kohde-id})))

          (q-yllapitokohteet/merkitse-kohteen-lahetystiedot!
            db
            {:lahetetty (pvm/nyt)
             :onnistunut kohteen-lahetys-onnistunut?
             :lahetysvirhe virhe-viesti
             :kohdeid kohde-id})))
      (when-not onnistunut?
        {:virhe virhe-viestit}))))

(defn lisaa-http-parametri [parametrit avain arvo]
  (if arvo
    (assoc parametrit avain arvo)
    parametrit))

(defn hae-kohteen-paallystysilmoitus [db kohde-id]
  (let [ilmoitus (first (q-paallystys/hae-paallystysilmoitus-kohdetietoineen-paallystyskohteella db {:paallystyskohde kohde-id}))]
    (konv/jsonb->clojuremap ilmoitus :ilmoitustiedot)))

(defn hae-alikohteet [db kohde-id paallystysilmoitus]
  (let [alikohteet (q-yha-tiedot/hae-yllapitokohteen-kohdeosat db {:yllapitokohde kohde-id})
        osoitteet (get-in paallystysilmoitus [:ilmoitustiedot :osoitteet])]
    (mapv (fn [alikohde]
            (let [id (:id alikohde)
                  ilmoitustiedot (first (filter #(= id (:kohdeosa-id %)) osoitteet))]
              (apply merge ilmoitustiedot alikohde)))
          alikohteet)))

(defn hae-kohteen-tiedot [db kohde-id]
  (if-let [kohde (-> (q-yllapitokohteet/hae-yllapitokohde db {:id kohde-id})
                     first
                     ;; Uudessa YHA-mallissa pääkohteella ei ole ajorataa taikka kaistaa
                     (dissoc :tr-ajorata :tr-kaista))]
    (let [maaramuutokset (:tulos (maaramuutokset/hae-ja-summaa-maaramuutokset
                                   db {:urakka-id (:urakka kohde) :yllapitokohde-id kohde-id}))
          paallystysilmoitus (hae-kohteen-paallystysilmoitus db kohde-id)
          paallystysilmoitus (assoc paallystysilmoitus :maaramuutokset maaramuutokset)
          paallystysilmoitus (if (= (:versio paallystysilmoitus) 2)
                               (let [keep-some (fn [map-jossa-on-nil]
                                                 (into {} (filter
                                                            (fn [[_ arvo]] (some? arvo))
                                                            map-jossa-on-nil)))
                                     alustatoimet (->> (q-paallystys/hae-pot2-alustarivit db {:pot2_id (:id paallystysilmoitus)})
                                                       (map keep-some)
                                                       (into []))]
                                 (println "petar evo ga lista " (pr-str alustatoimet))
                                 (assoc-in paallystysilmoitus [:ilmoitustiedot :alustatoimet] alustatoimet))
                               paallystysilmoitus)
          alikohteet (hae-alikohteet db kohde-id paallystysilmoitus)]
      {:kohde kohde
       :alikohteet alikohteet
       :paallystysilmoitus paallystysilmoitus})
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

(defn hae-urakan-kohteet-yhasta [integraatioloki db {:keys [url kayttajatunnus salasana]} urakka-id hakijan-tunnus]
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
                               (lisaa-http-parametri "kayttaja" hakijan-tunnus))
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

(defn yhaan-lahetettava-sampoid
  "Palveluurakasta lähetetään YHA:an palvelusopimuksen sampoid, kokonaisurakasta lähetetään urakan sampoid."
  [urakan-yha-tiedot]
  (if (= (:sopimustyyppi urakan-yha-tiedot) "palvelusopimus")
    (:palvelusopimus-sampoid urakan-yha-tiedot)
    (:urakka-sampoid urakan-yha-tiedot)))

(defn laheta-kohteet-yhaan
  "Lähettää annetut kohteet YHA:an. Mikäli kohteiden lähetys epäonnistuu, niiden päällystysilmoituksen
   lukko avataan, jotta mahdollisesti virheelliset tiedot voidaan korjata. Jos lähetys onnistuu, kohteiden
   päällystysilmoituksen lukitaan. Vuotta 2020 edeltäviä kohteita ei kaistamuutoksen jälkeen saa enää siirtää YHA:aan.
   Tämä on estetty funktiossa tarkista-lahetettavat-kohteet. Palauttaa true tai false sen mukaan onnistuiko kaikkien kohteiden lähetys."
  [integraatioloki db {:keys [url kayttajatunnus salasana]} urakka-id kohde-idt]
  (log/debug (format "Lähetetään urakan (id: %s) kohteet: %s YHAan URL:lla: %s." urakka-id kohde-idt url))
  (try+
    (integraatiotapahtuma/suorita-integraatio
      db integraatioloki "yha" "kohteiden-lahetys" nil
      (fn [konteksti]
        (if-let [urakka (first (q-yha-tiedot/hae-urakan-yhatiedot db {:urakka urakka-id}))]
          (let [urakka (assoc urakka :harjaid urakka-id
                                     :sampoid (yhaan-lahetettava-sampoid urakka))
                kohteet (mapv #(hae-kohteen-tiedot db %) kohde-idt)
                url (str url "toteumatiedot")
                _ (println "petar evo ovo ce da muodosta " (pr-str kohteet))
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

(defn poista-kohde-yhasta
  [integraatioloki db {:keys [url kayttajatunnus salasana]} yha-kohde-id]
  (integraatiotapahtuma/suorita-integraatio
    db integraatioloki "yha" "poista-kohde" nil
    (fn [konteksti]
      (let [url (str url "toteumakohde/" yha-kohde-id)
            http-asetukset {:metodi         :DELETE
                            :url            url
                            :kayttajatunnus kayttajatunnus
                            :salasana       salasana}
            {body :body} (integraatiotapahtuma/laheta konteksti :http http-asetukset)]
        (kasittele-kohteen-poistamisen-vastaus body yha-kohde-id)))))

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
    (laheta-kohteet-yhaan (:integraatioloki this) (:db this) asetukset urakka-id kohde-idt))
  (poista-kohde [this yha-kohde-id]
    (poista-kohde-yhasta (:integraatioloki this) (:db this) asetukset yha-kohde-id)))
