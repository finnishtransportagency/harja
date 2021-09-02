(ns harja.palvelin.integraatiot.velho.velho-komponentti
  (:require [com.stuartsierra.component :as component]
            [hiccup.core :refer [html]]
            [taoensso.timbre :as log]
            [harja.kyselyt.koodistot :as koodistot]
            [harja.kyselyt.konversio :as konversio]
            [harja.kyselyt.paallystys-kyselyt :as q-paallystys]
            [harja.kyselyt.yllapitokohteet :as q-yllapitokohteet]
            [harja.kyselyt.yha :as q-yha-tiedot]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [harja.palvelin.integraatiot.velho.sanomat.paallysrakenne-lahetyssanoma :as kohteen-lahetyssanoma]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [harja.palvelin.integraatiot.yha.yha-komponentti :as yha]
            [harja.palvelin.palvelut.yllapitokohteet.paallystys :as paallystys]
            [harja.pvm :as pvm]
            [clojure.core.memoize :as memoize]
            [clojure.java.jdbc :as jdbc]
            [clojure.data.json :as json]
            [clojure.string :as str])
  (:use [slingshot.slingshot :only [throw+ try+]]))

(defprotocol VelhoRajapinnat
  (laheta-kohteet [this urakka-id kohde-idt]))

(defn hae-kohteen-tiedot [db kohde-id]
  (let [paallystysilmoitus (first (q-paallystys/hae-paallystysilmoitus-kohdetietoineen-paallystyskohteella
                                    db
                                    {:paallystyskohde kohde-id}))
        _ (assert (= 2 (:versio paallystysilmoitus)) "Päällystysilmoituksen versio täytyy olla 2")
        poista-onnistuneet (fn [rivit]
                             (remove #(= "onnistunut" (:velho_rivi_lahetyksen_tila %)) rivit))
        paallystekerrokset (q-paallystys/hae-pot2-paallystekerrokset db {:pot2_id (:id paallystysilmoitus)})
        paallystekerrokset (poista-onnistuneet paallystekerrokset)
        alustat  (let [keep-some (fn [map-jossa-on-nil]
                                   (into {} (filter
                                              (fn [[_ arvo]] (some? arvo))
                                              map-jossa-on-nil)))
                       alustatoimet (->> (q-paallystys/hae-pot2-alustarivit db {:pot2_id (:id paallystysilmoitus)})
                                         (map keep-some)
                                         poista-onnistuneet
                                         (into []))]
                   alustatoimet)]
    {:paallystekerrokset (filter #(= 1 (:jarjestysnro %)) paallystekerrokset)
     :alustat alustat
     :paallystysilmoitus paallystysilmoitus}))

(defn kasittele-velhon-vastaus [db sisalto otsikot paivita-fn]
  (log/debug (format "Velho palautti kirjauksille vastauksen: sisältö: %s, otsikot: %s" sisalto otsikot))
  (let [vastaus (try (json/read-str sisalto :key-fn keyword)
                     (catch Throwable e
                       {:virheet [{:selite (.getMessage e)}]
                        :sanoman-lukuvirhe? true}))
        velho-oid (:oid vastaus)
        virheet (:virheet vastaus)                          ; todo emme tiedä miten virheet ilmoitetaan velholta
        onnistunut? (and (some? velho-oid) (empty? virheet))
        virhe-viesti (str "Velho palautti seuraavat virheet: " (str/join ", " virheet))]

    (if onnistunut?
      (do
        (log/info (str "Rivin lähetys velhoon onnistui " velho-oid))
        (paivita-fn "onnistunut" velho-oid)
        true)
      (do
        (log/error (str "Virheitä rivin lähetyksessä velhoon: " virheet))
        (paivita-fn "epaonnistunut" virhe-viesti)
        false))))

(defn laheta-kohteet-velhoon [integraatioloki db {:keys [paallystetoteuma-url token-url kayttajatunnus salasana]} urakka-id kohde-idt]
  (log/debug (format "Lähetetään urakan (id: %s) kohteet: %s Velhoon URL:lla: %s." urakka-id kohde-idt paallystetoteuma-url))
  (when (not (str/blank? paallystetoteuma-url))
    (try+
     (integraatiotapahtuma/suorita-integraatio
       db integraatioloki "velho" "kohteiden-lahetys" nil
       (fn [konteksti]
         (if-let [urakka (first (q-yha-tiedot/hae-urakan-yhatiedot db {:urakka urakka-id}))]
           (let [hae-velho-token (fn []
                                   (let [http-asetukset {:metodi :POST
                                                                      :url token-url
                                                                      :kayttajatunnus kayttajatunnus
                                                                      :salasana salasana}
                                         kutsudata "grant_type=client_credentials"
                                         vastaus (integraatiotapahtuma/laheta konteksti :http http-asetukset kutsudata)
                                         vastaus-body (json/read-str (:body vastaus))
                                         token (get vastaus-body "access_token")]
                                     token))
                 hae-velho-token (memoize/ttl hae-velho-token :ttl/threshold 3000000)
                 token (hae-velho-token)
                 urakka (assoc urakka :harjaid urakka-id
                                      :sampoid (yha/yhaan-lahetettava-sampoid urakka))
                 kohteet (mapv #(hae-kohteen-tiedot db %) kohde-idt)
                 kohde-id (first kohde-idt)                 ; oletan että on vain yksi
                 kutsudata (kohteen-lahetyssanoma/muodosta urakka (first kohteet) (partial koodistot/konversio db))
                 ainakin-yksi-rivi-onnistui? (atom false)
                 kohteen-lahetys-onnistunut? (atom true)
                 laheta-rivi-velhoon (fn [kuorma paivita-fn]
                                       (try+
                                         (let [otsikot {"Content-Type" "text/json; charset=utf-8"
                                                        "Authorization" (str "Bearer " token)}
                                               http-asetukset {:metodi :POST
                                                               :url paallystetoteuma-url
                                                               :otsikot otsikot}
                                               kuorma-json (json/write-str kuorma :value-fn konversio/pvm->json)
                                               {body :body headers :headers} (integraatiotapahtuma/laheta konteksti :http http-asetukset kuorma-json)
                                               onnistunut? (kasittele-velhon-vastaus db body headers paivita-fn)]
                                           (reset! kohteen-lahetys-onnistunut? (and @kohteen-lahetys-onnistunut? onnistunut?))
                                           (reset! ainakin-yksi-rivi-onnistui? (or @ainakin-yksi-rivi-onnistui? onnistunut?)))
                                         (catch [:type virheet/+ulkoinen-kasittelyvirhe-koodi+] {:keys [virheet]}
                                           (log/error "Päällystysilmoituksen rivin lähetys Velhoon epäonnistui. Virheet: " virheet)
                                           (reset! kohteen-lahetys-onnistunut? false)
                                           (paivita-fn "epaonnistunut" (str virheet)))))
                 paivita-paallystekerros (fn [id tila vastaus]
                                           (q-paallystys/merkitse-paallystekerros-lahetystiedot-velhoon!
                                             db
                                             {:aikaleima (pvm/nyt)
                                              :tila tila
                                              :lahetysvastaus vastaus
                                              :id id}))
                 paivita-alusta (fn [id tila vastaus]
                                  (q-paallystys/merkitse-alusta-lahetystiedot-velhoon!
                                    db
                                    {:aikaleima (pvm/nyt)
                                     :tila tila
                                     :lahetysvastaus vastaus
                                     :id id}))
                 paivita-yllapitokohde (fn [tila vastaus]
                                         (q-yllapitokohteet/merkitse-kohteen-lahetystiedot-velhoon!
                                           db
                                           {:aikaleima (pvm/nyt)
                                            :tila tila
                                            :lahetysvastaus vastaus
                                            :kohdeid kohde-id}))]
             (doseq [paallystekerros (:paallystekerros kutsudata)]
               (laheta-rivi-velhoon paallystekerros
                                    (partial paivita-paallystekerros (get-in paallystekerros [:ominaisuudet :korjauskohdeosan-ulkoinen-tunniste]))))
             (doseq [alusta (:alusta kutsudata)]
               (laheta-rivi-velhoon alusta
                                    (partial paivita-alusta (get-in alusta [:ominaisuudet :korjauskohdeosan-ulkoinen-tunniste]))))
             (if @kohteen-lahetys-onnistunut?
               (do (q-paallystys/lukitse-paallystysilmoitus! db {:yllapitokohde_id kohde-id})
                   (paivita-yllapitokohde "valmis" nil))
               (do (log/error (format "Kohteen (id: %s) lähetys epäonnistui. Virhe: \"%s\"" kohde-id "virhe viesti"))
                   (q-paallystys/avaa-paallystysilmoituksen-lukko! db {:yllapitokohde_id kohde-id})
                   (paivita-yllapitokohde (if @ainakin-yksi-rivi-onnistui? "osittain-onnistunut" "epaonnistunut") nil)))))))
     (catch [:type virheet/+ulkoinen-kasittelyvirhe-koodi+] {:keys [virheet]}
       (log/error "Päällystysilmoituksen lähetys Velhoon epäonnistui. Virheet: " virheet)
       false))))

(defn kasittele-tievelhon-vastaus [db sisalto otsikot paivita-fn]
  (log/debug (format "Tievelho palautti kirjauksille vastauksen: sisältö: %s, otsikot: %s" sisalto otsikot))
  (let [vastaus (try (json/read-str sisalto :key-fn keyword)
                     (catch Throwable e
                       {:virheet [{:selite (.getMessage e)}]
                        :sanoman-lukuvirhe? true}))
        velho-oid (:oid vastaus)
        virheet (:virheet vastaus)                          ; todo emme tiedä miten virheet ilmoitetaan tievelholta
        onnistunut? (and (some? velho-oid) (empty? virheet))
        virhe-viesti (str "Tievelho palautti seuraavat virheet: " (str/join ", " virheet))]

    (if onnistunut?
      (do
        (log/info (str "Haku tievelhosta onnistui " velho-oid))
        (paivita-fn "onnistunut" velho-oid)
        true)
      (do
        (log/error (str "Virheitä haettaessa tievelhosta: " virheet))
        (paivita-fn "epaonnistunut" virhe-viesti)
        false))))

(defn hae-tievelhosta [integraatioloki db {:keys [url token-url kayttajatunnus salasana]}]
  (log/debug (format "Haetaan Tievelhosta URL:lla: %s." url))
  (when (not (str/blank? url))
    (try+
      (integraatiotapahtuma/suorita-integraatio
        db integraatioloki "tievelho" "jotain-haku" nil
        (fn [konteksti]
          (let [url url]
            (let [hae-velho-token (fn []
                                    (let [http-asetukset {:metodi :POST
                                                          :url token-url
                                                          :kayttajatunnus kayttajatunnus
                                                          :salasana salasana}
                                          kutsudata "grant_type=client_credentials"
                                          vastaus (integraatiotapahtuma/laheta konteksti :http http-asetukset kutsudata)
                                          vastaus-body (json/read-str (:body vastaus))
                                          token (get vastaus-body "access_token")]
                                      token))
                  hae-velho-token (memoize/ttl hae-velho-token :ttl/threshold 3000000)
                  token (hae-velho-token)

                  ;kutsudata (kohteen-lahetyssanoma/muodosta urakka (first kohteet) (partial koodistot/konversio db))
                  ;aitojen-haku-onnistunut? (atom true)
                  ;hae-tievelhosta (fn [kuorma paivita-fn]
                  ;                      (try+
                  ;                        (let [otsikot {"Content-Type" "text/json; charset=utf-8"
                  ;                                       "Authorization" (str "Bearer " token)}
                  ;                              http-asetukset {:metodi  :GET
                  ;                                              :url     url
                  ;                                              :otsikot otsikot}
                  ;
                  ;                              {body :body headers :headers} (integraatiotapahtuma/laheta konteksti :http http-asetukset nil)
                  ;                              onnistunut? (kasittele-tievelhon-vastaus db body headers paivita-fn)]
                  ;                              (reset! aitojen-haku-onnistunut? onnistunut?)
                  ;                        (catch [:type virheet/+ulkoinen-kasittelyvirhe-koodi+] {:keys [virheet]}
                  ;                          (log/error "Päällystysilmoituksen rivin lähetys Velhoon epäonnistui. Virheet: " virheet)
                  ;                          (reset! aitojen-haku-onnistunut? false)
                  ;                          (paivita-fn "epaonnistunut" (str virheet))))))
                  ;paivita-alusta (fn [id tila vastaus]
                  ;                 (q-paallystys/merkitse-alusta-lahetystiedot-velhoon!
                  ;                   db
                  ;                   {:aikaleima (pvm/nyt)
                  ;                    :tila tila
                  ;                    :lahetysvastaus vastaus
                  ;                    :id id}))
                  ] (println "Koodia puuttuu vielä")
                    ;(doseq [paallystekerros (:paallystekerros kutsudata)]
                    ;  (laheta-rivi-velhoon paallystekerros
                    ;                       (partial paivita-paallystekerros (get-in paallystekerros [:ominaisuudet :korjauskohdeosan-ulkoinen-tunniste]))))
                    ;(doseq [alusta (:alusta kutsudata)]
                    ;  (hae-tievelhosta alusta
                    ;                       (partial paivita-alusta (get-in alusta [:ominaisuudet :korjauskohdeosan-ulkoinen-tunniste]))))
                    ;(if @kohteen-lahetys-onnistunut?
                    ;  (do (q-paallystys/lukitse-paallystysilmoitus! db {:yllapitokohde_id kohde-id})
                    ;      (paivita-yllapitokohde "valmis" nil))
                    ;  (do (log/error (format "Kohteen (id: %s) lähetys epäonnistui. Virhe: \"%s\"" kohde-id "virhe viesti"))
                    ;      (q-paallystys/avaa-paallystysilmoituksen-lukko! db {:yllapitokohde_id kohde-id})
                    ;      (paivita-yllapitokohde (if @ainakin-yksi-rivi-onnistui? "osittain-onnistunut" "epaonnistunut") nil)))))))
                    ;(catch [:type virheet/+ulkoinen-kasittelyvirhe-koodi+] {:keys [virheet]}
                    ;  (log/error "Päällystysilmoituksen lähetys Velhoon epäonnistui. Virheet: " virheet)
                    ;  false)
                    )))))))

(defrecord Velho [asetukset]
  component/Lifecycle
  (start [this] this)
  (stop [this] this)

  VelhoRajapinnat
  (laheta-kohteet [this urakka-id kohde-idt]
    (laheta-kohteet-velhoon (:integraatioloki this) (:db this) asetukset urakka-id kohde-idt)))
