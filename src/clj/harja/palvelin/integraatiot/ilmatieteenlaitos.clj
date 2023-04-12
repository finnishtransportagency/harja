(ns harja.palvelin.integraatiot.ilmatieteenlaitos
  (:require [org.httpkit.client :as http]
            [taoensso.timbre :as log]
            [harja.tyokalut.xml :as xml]
            [clojure.zip :refer [xml-zip]]
            [clojure.data.zip.xml :as z]
            [slingshot.slingshot :refer [throw+]]
            [clojure.string :as str]))
            

(defn- lue-lampotilat [tieindeksi]
  (z/xml-> tieindeksi
           :urakka
           (fn [urakka]
             (let [jakso (z/xml1-> urakka :jakso)
                   arvo (fn [avain]
                          (z/xml1-> jakso avain z/text #(Double/parseDouble %)))]
               {:urakka-id (z/attr urakka :id)
                :kohde (z/xml1-> urakka (z/attr :kohde))
                :keskilampotila (arvo :keskilampotila)
                :pitkakeskilampotila (arvo :ilmastollinen_keskiarvo)
                :keskilampotilan-ilm-ka-erotus (arvo :keskilampotilan_ilm_ka_erotus)
                :ilmastollinen-alaraja (arvo :ilmastollinen_alaraja)
                :ilmastollinen-ylaraja (arvo :ilmastollinen_ylaraja)}))))

(defn hae-talvikausi
  "Haetaan talvikausi ilmatieteenlaitoksen rajapinnasta.
   1971-2000 väli käyttää vanhempaa rajapintaa, joka ei ota vastaan aikaväliä. Jos käytät tätä, jätä keskiarvon-alkuvuosi tyhjäksi.
   1981-2010 ja 1991-2020 käyttävät uudempaa rajapintaa, ja aikaväli laitetaan climatology-parametrissa. Välitä tämä keskiarvon-alkuvuosi-parametrissa. "
  [endpoint-url talvikauden-alkuvuosi keskiarvon-alkuvuosi]
  (log/debug "hae talvikausi ilmatieteenlaitokselta: " endpoint-url " talvikauden alkuvuosi " talvikauden-alkuvuosi)
  ;; TODO: Käytä integraatiotapahtumaa.
  (let [{:keys [status body error headers]} @(http/post endpoint-url
                                               {:query-params (merge
                                                                {"season" (str talvikauden-alkuvuosi
                                                                                 "-"
                                                                                 (inc talvikauden-alkuvuosi))
                                                                 "newversion" 1}
                                                                (when keskiarvon-alkuvuosi
                                                                  {"climatology" (str keskiarvon-alkuvuosi
                                                                                   "-"
                                                                                   (+ keskiarvon-alkuvuosi 29))}))
                                                :timeout 10000})]
    (log/debug "STATUS: " status)
    (log/debug "HEADERS: " headers)

    (if error
      (do (log/warn "Ilmatieteenlaitoksen palvelun kutsu epäonnistui: " status error)
          (throw+ {:type :ilmatieteenlaitoksen-lampotilahaku-epaonnistui
                   :error error}))
      (if (not (str/includes? (:content-type headers) "text/xml"))
        (throw+ {:type :ilmatieteenlaitoksen-lampotilahaku-epaonnistui
                 :error body})
        (-> body 
            (xml/lue "ISO-8859-1")
            lue-lampotilat)))))
