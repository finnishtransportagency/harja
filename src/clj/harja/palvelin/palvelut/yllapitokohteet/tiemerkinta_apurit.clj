(ns harja.palvelin.palvelut.yllapitokohteet.tiemerkinta-apurit
  "Tiemerkintöjen kustannuksien apufunktiot"
  (:require
   [taoensso.timbre :as log]
   [slingshot.slingshot :refer [throw+]]

   [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet]))

(defn default-kustannuslista
  "Palauttaa oletus nolla-arvot vuosille, joille ei ole merkitty kustannuksia
   urakan alkamisvuoden ja loppumisvuoden perusteella."
  [urakka-id alkuvuosi loppuvuosi]
  (for [x (range alkuvuosi (+ 1 loppuvuosi))]
    (assoc {} :urakka urakka-id :kustannusvuosi x :kustannus 0 :pk1 0 :pk2 0 :pk3 0)))

(defn tee-valmis-kustannuslista [vastaus default-lista]
  (let [filteroi-arvoilla-fn (fn [data arvot]
                               (filterv #(not (some (set arvot) (vals %))) data))
        filteroi-vuodet (into [] (map :kustannusvuosi vastaus))
        filteroitu-lista (filteroi-arvoilla-fn default-lista filteroi-vuodet)]
    (sort-by :kustannusvuosi (concat vastaus filteroitu-lista))))

(defn validoi-kustannuskirjaus-rivi
  "Validointi tiemerkintöjen korjaus kustannuksille"
  [rivi]
  (let [summa (->> [:pk1 :pk2 :pk3]
                (map #(get rivi % 0))
                (reduce +)
                float)]
    (when-not (= 100.0 summa)
      (log/error "PK-osuuksien summan on oltava 100, saatiin:" summa)
      (throw+ {:type virheet/+viallinen-kutsu+
               :virheet [{:koodi virheet/+sisainen-kasittelyvirhe-koodi+
                          :viesti "PK-osuuksien summan on oltava 100"}]}))))

(defn laske-korjaukset-yhteen
  "TODO .. Suodattaa korjaus kustannukset vuoden perusteella 
   Palauttaa vectorin ryhmitettynä:
   :id        Grid tunniste 
   :tyyppi    :korjaus
   :hinta     Summattu hinta"
  [korjaus-kustannukset [alku loppu]]
  (let [suodatettu (filter (fn [{vuosi :kustannusvuosi}]
                             (let [kustannuksen-pvm (pvm/vuoden-eka-pvm vuosi)]
                               (and
                                 (not (pvm/ennen? kustannuksen-pvm alku))
                                 (not (pvm/jalkeen? kustannuksen-pvm loppu)))))
                     korjaus-kustannukset)]
    [{:id     (gensym)
      :tyyppi :korjaus
      :hinta  (reduce + 0 (map :kustannus suodatettu))

      :pk1 (reduce + 0 (map :kustannus suodatettu))}]))
