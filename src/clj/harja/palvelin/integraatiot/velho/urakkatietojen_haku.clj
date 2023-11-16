(ns harja.palvelin.integraatiot.velho.urakkatietojen-haku
  (:require [clojure.data.json :as json]
            [taoensso.timbre :as log]
            [harja.kyselyt.urakat :as q-urakat]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [harja.palvelin.integraatiot.velho.yhteiset :as velho-yhteiset]))

(defn- hae-mh-urakoiden-oidit-velhosta [integraatioloki db asetukset urakka-numerot]
  (integraatiotapahtuma/suorita-integraatio db integraatioloki "velho" "velho-oidien-haku" nil
    (fn [konteksti]
      (let [{:keys [token-url
                    varuste-kayttajatunnus
                    varuste-salasana
                    varuste-api-juuri-url]} asetukset]
        (when-let [token (velho-yhteiset/hae-velho-token token-url varuste-kayttajatunnus varuste-salasana konteksti
                           (fn [x]
                             (log/error "Virhe velho token haussa" x)))]
          (let [otsikot {"Content-Type" "application/json"
                         "Authorization" (str "Bearer " token)}
                kohdeluokka "urakka/maanteiden-hoitourakka"
                ominaisuudet "ominaisuudet"
                http-asetukset {:metodi :POST
                                :otsikot otsikot
                                :url (str varuste-api-juuri-url "/hakupalvelu/api/v1/haku/kohdeluokat")}

                payload {:asetukset {:tyyppi "kohdeluokkahaku"
                                     :palautettavat-kentat [[kohdeluokka, ominaisuudet, "nimi"],
                                                            [kohdeluokka, ominaisuudet, "urakkakoodi"],
                                                            ["yleiset/perustiedot", "oid"]]}
                         :kohdeluokat [kohdeluokka]
                         :lauseke (keep identity
                                    ["kohdeluokka" kohdeluokka
                                     ["joukossa" [kohdeluokka ominaisuudet "urakkakoodi"]
                                      urakka-numerot]])}
                {vastaus :body} (integraatiotapahtuma/laheta konteksti :http http-asetukset (json/write-str payload))]
            (:osumat (json/read-str vastaus :key-fn keyword))))))))

(defn- paivita-oid-ja-lyhytnimi [db urakka]
  (q-urakat/paivita-velho_oid-ja-lyhytnimi-urakalle! db {:urakkanro (:urakkakoodi (:ominaisuudet urakka))
                                                         :velho_oid (:oid urakka)
                                                         :lyhyt_nimi (:nimi (:ominaisuudet urakka))}))

(defn hae-mh-urakoiden-oidit-ja-lyhytnimet
  [integraatioloki db asetukset]
  (let [urakka-numerot (mapv :urakkanro (q-urakat/hae-mh-urakat-ilman-velho-oidia db))
        _ (log/debug "Urakkanumerot joita vastaava velho oid null: " (pr-str urakka-numerot))]
    (if (empty? urakka-numerot)
      (log/debug "Urakkataulussa ei tyhjiä velho oid arvoja, ei tarpeen hakea velhosta")
      (let [urakat (hae-mh-urakoiden-oidit-velhosta integraatioloki db asetukset urakka-numerot)
            _ (log/debug "Saatin velhosta urakat " urakat)]
        (doseq [urakka urakat]
          (paivita-oid-ja-lyhytnimi db urakka))))))
