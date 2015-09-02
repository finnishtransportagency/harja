(ns harja.palvelin.integraatiot.api.json-esimerkkivalidoinnit
  (:require [clojure.test :refer [deftest is use-fixtures]]
            [harja.testi :refer :all]
            [harja.tyokalut.json_validointi :as json-validointi]
            [harja.palvelin.integraatiot.api.tyokalut.skeemat :as skeemat]
            [harja.palvelin.integraatiot.api.tyokalut.esimerkit :as esimerkit]
            [clojure.java.io :as io]))

(deftest validoi-jsonit
  (let [skeemapolku-esimerkkipolku [[skeemat/+urakan-haku-vastaus+ esimerkit/+urakan-haku-vastaus+]
                                    [skeemat/+urakoiden-haku-vastaus+ esimerkit/+urakoiden-haku-vastaus+]

                                    [skeemat/+havainnon-kirjaus+ esimerkit/+havainnon-kirjaus+]

                                    [skeemat/+ilmoituskuittauksen-kirjaaminen+ esimerkit/+ilmoituskuittauksen-kirjaaminen+]
                                    [skeemat/+ilmoitusten-haku+ esimerkit/+ilmoitusten-haku+]
                                    [skeemat/+tietyoilmoituksen-kirjaus+ esimerkit/+tietyoilmoituksen-kirjaus+]

                                    #_[skeemat/+pistetoteuman-kirjaus+ esimerkit/+pistetoteuman-kirjaus+]; FIXME Rikki "JSON ei ole validia: /pistetoteuma/toteuma: Ylimääräisiä kenttiä: selite"
                                    [skeemat/+reittitoteuman-kirjaus+ esimerkit/+reittitoteuman-kirjaus+]

                                    #_[skeemat/+poikkeamien-kirjaus+ esimerkit/+poikkeamien-kirjaus+] ; FIXME Rikki "JSON ei ole validia: /poikkeamat[0]/poikkeama: Ylimääräisiä kenttiä: kohde"}
                                    [skeemat/+turvallisuuspoikkeamien-kirjaus+ esimerkit/+turvallisuuspoikkeamien-kirjaus+]

                                    [skeemat/+tielupien-haku+ esimerkit/+tielupien-haku+]
                                    [skeemat/+tielupien-haku-vastaus+ esimerkit/+tielupien-haku-vastaus+]

                                    [skeemat/+tietolajien-haku+ esimerkit/+tietolajien-haku+]

                                    [skeemat/+varusteen-haku-vastaus+ esimerkit/+varusteen-haku-vastaus+]
                                    [skeemat/+varusteiden-haku-vastaus+ esimerkit/+varusteiden-haku-vastaus+]
                                    #_[skeemat/+varusteen-lisays+ esimerkit/+varusteen-lisays+] ; FIXME JSON ei ole validia: /lisaaja: Ylimääräisiä kenttiä: ytunnus"
                                    #_[skeemat/+varusteen-paivitys+ esimerkit/+varusteen-paivitys+] ; FIXME Rikki JSON ei ole validia: /paivittaja: Ylimääräisiä kenttiä: ytunnus
                                    #_[skeemat/+varusteen-poisto+ esimerkit/+varusteen-poisto+] ; FIXME Rikki JSON ei ole validia: /paivittaja: Ylimääräisiä kenttiä: ytunnus
                                    #_[skeemat/+varustetoteuman-kirjaus+ esimerkit/+varustetoteuman-kirjaus+] ; FIXME Rikki JSON ei ole validia: /varustetoteuma/toteuma: Väärä tyyppi. Tyyppi: :array-like, arvo: {\"tunniste\" {\"id\" 3} jne.

                                    [skeemat/+siltatarkastuksen-kirjaus+ esimerkit/+siltatarkastuksen-kirjaus+]
                                    [skeemat/+tiestotarkastuksen-kirjaus+ esimerkit/+tiestotarkastuksen-kirjaus+]
                                    [skeemat/+soratietarkastuksen-kirjaus+ esimerkit/+soratietarkastuksen-kirjaus+]
                                    [skeemat/+talvihoitotarkastuksen-kirjaus+ esimerkit/+talvihoitotarkastuksen-kirjaus+]

                                    [skeemat/+paivystajatietojen-kirjaus+ esimerkit/+paivystajatietojen-kirjaus+]
                                    [skeemat/+paivystajatietojen-haku+ esimerkit/+paivystajatietojen-haku+]
                                    #_[skeemat/+paivystajatietojen-haku-vastaus+ esimerkit/+paivystajatietojen-haku-vastaus+] ; FIXME Rikki "JSON ei ole validia: /urakat[0]/urakka: Ylimääräisiä kenttiä: paivystykset"}

                                    [skeemat/+tyokoneenseuranta-kirjaus+ esimerkit/+tyokoneenseuranta-kirjaus+]]
        skeemapolku-esimerkkidata (mapv
                                    (fn [[skeemapolku esimerkkipolku]]
                                      (let [esimerkkidata (slurp (io/resource esimerkkipolku))]
                                        [skeemapolku esimerkkidata]))
                                    skeemapolku-esimerkkipolku)]
    (doseq [validoitava skeemapolku-esimerkkidata]
      (json-validointi/validoi (first validoitava) (second validoitava)))))