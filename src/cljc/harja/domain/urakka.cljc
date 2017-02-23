(ns harja.domain.urakka
  "Määrittelee urakka nimiavaruuden specit, jotta urakan tietoja voi käyttää namespacetuilla
  keywordeilla, esim. {:urakka/id 12}"
  (:require [clojure.spec :as s]
            #?@(:clj [[clojure.future :refer :all]])))

(s/def ::id nat-int?)
(s/def ::nimi string?)

(s/def ::alkupvm inst?)
(s/def ::loppupvm inst?)

(s/def ::sampoid string?)

(s/def ::tyyppi #{:hoito :paallystys :valaistus :tiemerkinta
                  :tekniset-laitteet :siltakorjaus :paikkaus})

(s/def ::urakka (s/keys :req [::id]
                        :opt [::nimi ::sampoid ::tyyppi
                              ::alkupvm ::loppupvm
                              ::urakoitsija
                              ::hallintayksikko]))

;; Urakkakohtainen kysely, joka vaatii vain urakan id:n.
;; Tätä speciä on hyvä käyttää esim. palveluiden, jotka hakevat
;; urakan tietoja, kyselyspecinä.
(s/def ::urakka-kysely (s/keys :req [::id]))
