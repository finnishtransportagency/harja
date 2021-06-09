(ns harja.palvelin.integraatiot.velho.velho-komponentti
  (:require [com.stuartsierra.component :as component]
            [hiccup.core :refer [html]]
            [taoensso.timbre :as log]
            [harja.kyselyt.koodistot :refer [konversio]]
            [harja.kyselyt.paallystys :as q-paallystys]
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
  (log/debug format "Velho palautti kirjauksille vastauksen: sisältö: %s, otsikot: %s" sisalto otsikot)
  (jdbc/with-db-transaction [db db]
                            (let [vastaus (try (json/read-str sisalto)
                                               (catch Throwable e
                                                 {:virheet [{:selite (.getMessage e)}]
                                                  :sanoman-lukuvirhe? true}))
                                  _ (println "petar odgovor od servera " (pr-str otsikot))
                                  _ (println "petar vastaus " (pr-str vastaus))
                                  velho-oid (:oid vastaus)
                                  virheet (:virheet vastaus) ; todo emme tiedä millä virheet ilmoitetaan velholta
                                  onnistunut? (and (some? velho-oid) (empty? virheet))
                                  virhe-viesti (str "YHA palautti seuraavat virheet: " (str/join ", " virheet))]

                              (if onnistunut?
                                (do
                                  (log/info "Rivin lähetys velhoon onnistui")
                                  (paivita-fn "onnistunut" velho-oid)
                                  true)
                                (do
                                  (log/error (str "Virheitä rivin lähetyksessä velhoon: " virheet))
                                  (paivita-fn "epaonnistunut" virhe-viesti)
                                  false)))))

(defn laheta-kohteet-velhoon [integraatioloki db {:keys [paallystetoteuma-url token-url kayttajatunnus salasana]} urakka-id kohde-idt] ; petar ovo je rutina koja generise http zahteve
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
                 _ (println "petar da vidimo sta je sve dovukao " (pr-str kohteet))
                 kohde-id (first kohde-idt)                 ; oletan että on vain yksi
                 kutsudata (kohteen-lahetyssanoma/muodosta urakka (first kohteet) (partial konversio db))
                 _ (println "petar ovo ce da salje " (pr-str kutsudata))
                 ainakin-yksi-onnistunut? (atom false)
                 kohteen-lahetys-onnistunut? (atom true)
                 laheta-velhoon (fn [kuorma paivita-fn]
                                  (let [otsikot {"Content-Type" "text/json; charset=utf-8"
                                                 "Authorization" (str "Bearer " token)}
                                        http-asetukset {:metodi :POST
                                                        :url paallystetoteuma-url
                                                        :otsikot otsikot}
                                        {body :body headers :headers} (try
                                                                        (integraatiotapahtuma/laheta konteksti :http http-asetukset kuorma)
                                                                        (catch RuntimeException e
                                                                          {:body {:virheet [{:selite (.getMessage e)}]}}))
                                        onnistunut? (kasittele-velhon-vastaus db body headers paivita-fn)]
                                    (reset! kohteen-lahetys-onnistunut? (and @kohteen-lahetys-onnistunut? onnistunut?))
                                    (reset! ainakin-yksi-onnistunut? (or @ainakin-yksi-onnistunut? onnistunut?))))
                 paivita-paallystekerros (fn [id tila vastaus]
                                           false)
                 paivita-alusta (fn [id tila vastaus]
                                  false)
                 paivita-yllapitokohde (fn [tila vastaus]
                                         (q-yllapitokohteet/merkitse-kohteen-lahetystiedot-velhoon!
                                           db
                                           {:aikaleima (pvm/nyt)
                                            :tila tila
                                            :lahetysvastaus vastaus
                                            :kohdeid kohde-id}))]
             (doseq [paallystekerros (:paallystekerros kutsudata)]
               (laheta-velhoon paallystekerros
                               (partial paivita-paallystekerros (:kohdeosa-id paallystekerros))))
             (doseq [alusta (:alusta kutsudata)]
               (laheta-velhoon alusta
                               (partial paivita-alusta (:id alusta))))
             (if @kohteen-lahetys-onnistunut?
               (do (q-paallystys/lukitse-paallystysilmoitus! db {:yllapitokohde_id kohde-id})
                   (paivita-yllapitokohde "lahetyspalvelu" nil))
               (do (log/error (format "Kohteen (id: %s) lähetys epäonnistui. Virhe: \"%s\"" kohde-id "virhe viesti"))
                   (q-paallystys/avaa-paallystysilmoituksen-lukko! db {:yllapitokohde_id kohde-id})
                   (paivita-yllapitokohde (if @ainakin-yksi-onnistunut? "osittain-onnistunut" "epaonnistunut") nil)))))))
     (catch [:type virheet/+ulkoinen-kasittelyvirhe-koodi+] {:keys [virheet]}
       (log/error "Päällystysilmoituksen lähetys Velhoon epäonnistui. Virheet: " virheet)
       false))))

(defrecord Velho [asetukset]
  component/Lifecycle
  (start [this] this)
  (stop [this] this)

  VelhoRajapinnat
  (laheta-kohteet [this urakka-id kohde-idt]
    (laheta-kohteet-velhoon (:integraatioloki this) (:db this) asetukset urakka-id kohde-idt)))
