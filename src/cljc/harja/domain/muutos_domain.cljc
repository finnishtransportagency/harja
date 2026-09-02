(ns harja.domain.muutos-domain
  "Muutos-domainin määritykset"
  (:require [clojure.spec.alpha :as s]
            [harja.pvm :as pvm]
            [harja.domain.muokkaustiedot :as m]
            #?@(:clj [[harja.kyselyt.specql-db :refer [define-tables]]]
                :cljs [[specql.impl.registry]]))
  #?(:cljs (:require-macros [harja.kyselyt.specql-db :refer [define-tables]])))

(define-tables
  ["mhu_muutos" ::muutos
   harja.domain.muokkaustiedot/muokkaustiedot
   harja.domain.muokkaustiedot/poistettu?-sarake])

(def +muutostyo-valinnat+ {:erillisrahoitus "Erillisrahoituksella tehtävä muutostyö"
                           :poikkeama "Poikkeaminen tehtävä- ja määräluettelon määrästä"})

(def +muutos-kulu-tyypit+
  {:jjh-muutos "Muutos (Johto- ja hallintokorvaus)"
   :erillisrahoitettu-muutos "Muutostyö (erillisrahoitettu)"})

(def +muutostyypit-lomakkeella+
  "MHU muutosten mahdolliset tyypit. Näiden tulee matchata tietokannassa olevaan custom typeen MHU_MUUTOSTYYPPI"
  ["pysyva"
   "johto-ja-hallintokorvaus"
   "muutostyo"])

;; TODO: Tarkista lomakkeen valinna ja niiden nimitykset
(defn tyyppi-fmt
  "Palauttaa muutostyypin tietokannasta tulevan enumin nimen käyttöliittymää varten selkokielisenä. Esim. 'pysyva' -> 'Pysyvä'."
   [tyyppi urakan-sopimustyyppi]
  ({"pysyva" "Pysyvä muutos"
    "rahavaraus" "Rahavaraus"
    "johto-ja-hallintokorvaus" (if (= :mhu+ urakan-sopimustyyppi)
                                 "Kumppanuusmaksun muutos"
                                 "Johto- ja hallintokorvauksen muutos")
    "erillisrahoitettu" "Erillisrahoitettu"
    "toteutuneet-maarat" "Toteutuneet määrät"
    "maarapoikkeama" "Määräpoikkeama"
    "muutostyo" "Muutostyö (erillisrahoitettu)"} tyyppi))

(defn jjh-korvaus-muutos-vai-vahennys?
  "Johto- ja hallintokorvauksen muutos on :muutos jos urakka alkanut 1.10.2024 tai aiemmin, muutoin vähennys"
  [urakan-alkupvm]
  (if (pvm/ennen? urakan-alkupvm (pvm/->pvm "5.10.2024"))
    :muutos
    :vahennys))

(defn muutos-voimassa-kesken-hoitokauden?
  "Muutoksen voimassaolo alkaa kesken hoitovuoden?"
  [voimassa-alkaen hoitovuosi]
  (pvm/valissa? voimassa-alkaen (first hoitovuosi) (second hoitovuosi)))

(defn voimassa-alkaen-hoitovuodella-tai-jalkeen?
  [voimassa-alkaen hoitovuosi]
  (or
    (muutos-voimassa-kesken-hoitokauden? voimassa-alkaen hoitovuosi)
    (pvm/jalkeen? (second hoitovuosi) voimassa-alkaen)))

;; -- Pysyvän muutoksen lukitukset --
(defn jokin-hoitovuosien-indeksikorjaus-vahvistettu?
  "Palauttaa true, jos jonkin hoitovuoden indeksikorjaus on vahvistettu"
  [tavoitehinta-indeksikorjattu-per-hoitovuosi]
  (assert (map? tavoitehinta-indeksikorjattu-per-hoitovuosi))

  (some? (some true? (vals tavoitehinta-indeksikorjattu-per-hoitovuosi))))

(defn pysyva-muutos-voimassa-alkaen-lukittu?
  "Palauttaa true, jos voimassa-alkaen kenttä on lukittu muokkaukselta.
  Lukittu, jos:
  - Jonkin hoitovuoden alun tavoitehinta on vahvistettu
  - Pysyvä muutos sisältyy jonkin hoitovuoden vahvistettuun välikatselmuksen päätökseen"
  [tavoitehinta-indeksikorjattu-per-hoitovuosi]
  (assert (map? tavoitehinta-indeksikorjattu-per-hoitovuosi))

  ;; TODO: Välikatselmuksen päätökset tarkastus toteutetaan myöhemmin, HARJA-1767

  (jokin-hoitovuosien-indeksikorjaus-vahvistettu? tavoitehinta-indeksikorjattu-per-hoitovuosi))

(defn hoitovuoden-indeksikorjaus-vahvistettu?
  "Palauttaa true, jos kyseisen hoitovuoden indeksikorjaus on vahvistettu, eli hoitovuoden alun tavoitehinta on vahvistettu."
  [tavoitehinta-indeksikorjattu-per-hoitovuosi hoitovuosi]
  (let [hoitokauden-alkuvuosi (some-> hoitovuosi (first) (pvm/vuosi))
        indeksikorjaus-vahvistettu? (get tavoitehinta-indeksikorjattu-per-hoitovuosi hoitokauden-alkuvuosi false)]
    indeksikorjaus-vahvistettu?))

(defn pysyva-muutos-hoitovuosi-lukittu?
  "Palauttaa true, jos hoitovuosi on lukittu muokkaukselta.
  Lukittu, jos:
  - Hoitovuoden vaikutukset sisältyvät kyseisen hoitovuoden tavoitehintaan JA hoitovuoden alun tavoitehinta on vahvistettu
  - TAI hoitovuoden välikatselmuksen päätöksiä on tehty
  "
  [tavoitehinta-indeksikorjattu-per-hoitovuosi voimassa-alkaen hoitovuosi]
  (assert (map? tavoitehinta-indeksikorjattu-per-hoitovuosi))

  ;; TODO: Välikatselmuksen päätökset tarkastus toteutetaan myöhemmin, HARJA-1767

  ;; Mikäli muutos alkaa kesken hoitokauden, sillä ei ole merkitystä kyseisen hoitovuoden alun tavoitehinnan kannalta
  ;; Eli, muutosta ei ole tarpeen lukita vaikka tavoitehinta olisi vahvistettu
  (and
    (not (muutos-voimassa-kesken-hoitokauden? voimassa-alkaen hoitovuosi))

    ;; Jos muutoksen vaikutukset koskevat koko hoitovuotta, tarkistetaan onko hoitovuoden alun tavoitehinta vahvistettu
    (hoitovuoden-indeksikorjaus-vahvistettu? tavoitehinta-indeksikorjattu-per-hoitovuosi hoitovuosi)))

;; Muutoksissa käytettävä talvisuolakerroin on kovakoodattu tähän.
(def +talvisuolakerroin+ 0.7)
