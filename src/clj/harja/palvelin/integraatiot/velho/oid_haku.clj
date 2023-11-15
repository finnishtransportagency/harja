(ns harja.palvelin.integraatiot.velho.oid-haku
  (:require [clojure.data.json :as json]
            [taoensso.timbre :as log]
            [harja.kyselyt.urakat :as q-urakat]
            [harja.palvelin.integraatiot.integraatiotapahtuma :as integraatiotapahtuma]
            [harja.palvelin.integraatiot.velho.yhteiset :as velho-yhteiset]))

(defn- hae-mh-urakoiden-oidit-velhosta [integraatioloki db asetukset urakka-numerot]
  (integraatiotapahtuma/suorita-integraatio db integraatioloki "velho" "velho-oidien-haku" nil
    (fn [konteksti]
      (let [virheet (atom #{})
            {:keys [token-url
                    varuste-kayttajatunnus
                    varuste-salasana
                    varuste-api-juuri-url]} asetukset]
        (when-let [token (velho-yhteiset/hae-velho-token token-url varuste-kayttajatunnus varuste-salasana konteksti
                           (fn [x]
                             (swap! virheet conj (str "Virhe velho token haussa " x))
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
                         :kohdeluokat ["urakka/maanteiden-hoitourakka"]
                         :lauseke (keep identity
                                    ["kohdeluokka" kohdeluokka
                                     ["joukossa" [kohdeluokka ominaisuudet "urakkakoodi"]
                                      (map pr-str urakka-numerot)]])}
                {vastaus :body} (integraatiotapahtuma/laheta konteksti :http http-asetukset (json/write-str payload))]
            (:osumat (json/read-str vastaus :key-fn keyword))))))))

(defn- paivita-oid-ja-lyhytnimi [db urakka]
  (q-urakat/paivita-velho_oid-ja-lyhytnimi-urakalle! db {:urakkanro (:urakkakoodi (:ominaisuudet urakka))
                                                         :velho_oid (:oid urakka)
                                                         :lyhyt_nimi (:nimi (:ominaisuudet urakka))}))
(defn hae-mh-urakoiden-oidit
  [integraatioloki db asetukset]
  (let [urakka-numerot (map :urakkanro (q-urakat/hae-mh-urakoiden-urakka-numerot db))
        _ (log/debug "urakkanumerot joita vastaava velho oid null: " (pr-str urakka-numerot))]
    (if (empty? urakka-numerot)
      (log/debug "Urakkataulussa ei tyhjiä velho oid arvoja, ei tarpeen hakea velhosta")
      (let [urakat (hae-mh-urakoiden-oidit-velhosta integraatioloki db asetukset urakka-numerot)
            _ (log/debug "Saatin velhosta urakat " urakat)]
        (doseq [urakka urakat]
          (paivita-oid-ja-lyhytnimi db urakka))))))
