(ns harja.palvelin.integraatiot.tievelho.tievelho-komponentti
  (:require [hiccup.core :refer [html]]
            [taoensso.timbre :as log]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]
            [clojure.core.memoize :as memoize]
            [clojure.data.json :as json]
            [clojure.string :as str])
  (:use [slingshot.slingshot :only [throw+ try+]]))

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