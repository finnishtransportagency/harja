(ns harja.kyselyt.vesivaylat.toimenpiteet-test
  (:require [clojure.test :refer :all]

            [harja.kyselyt.vesivaylat.toimenpiteet :as q]

            [harja.domain.vesivaylat.toimenpide :as to]
            [harja.domain.vesivaylat.hinnoittelu :as h]
            [harja.domain.vesivaylat.tyo :as tyo]
            [harja.domain.vesivaylat.hinta :as hinta]
            [harja.domain.vesivaylat.turvalaitekomponentti :as tkomp]
            [harja.domain.vesivaylat.komponentin-tilamuutos :as komp-tila]
            [harja.domain.muokkaustiedot :as m]
            [harja.domain.liite :as liite]))

(deftest vaadi-toimenpiteet-kuuluvat-urakkaan
  (is (nil? (#'q/vaadi-toimenpiteet-kuuluvat-urakkaan*
               [{::to/urakka-id 1 ::to/id 1}]
               #{1}
               1)))

  (is (nil? (#'q/vaadi-toimenpiteet-kuuluvat-urakkaan*
               [{::to/urakka-id 1 ::to/id 1}
                {::to/urakka-id 1 ::to/id 2}
                {::to/urakka-id 1 ::to/id 3}]
               #{1 2 3}
               1)))

  (is (thrown? SecurityException
               (#'q/vaadi-toimenpiteet-kuuluvat-urakkaan*
               [{::to/urakka-id 2 ::to/id 1}]
               #{1}
               1)))

  (is (thrown? SecurityException
               (#'q/vaadi-toimenpiteet-kuuluvat-urakkaan*
               [{::to/urakka-id 1 ::to/id 1}
                {::to/urakka-id 2 ::to/id 2}
                {::to/urakka-id 1 ::to/id 3}]
               #{1 2 3}
               1)))

  (is (thrown? SecurityException
               (#'q/vaadi-toimenpiteet-kuuluvat-urakkaan*
               [{::to/urakka-id 1 ::to/id 1}
                {::to/urakka-id nil ::to/id 2}
                {::to/urakka-id 1 ::to/id 3}]
               #{1 2 3}
               1)))

  (is (thrown? SecurityException
               (#'q/vaadi-toimenpiteet-kuuluvat-urakkaan*
                [{::to/urakka-id 1 ::to/id 1}
                 {::to/urakka-id nil ::to/id 2}
                 {::to/urakka-id 1 ::to/id 3}]
                #{1 2 3}
                nil)))

  (is (thrown? SecurityException
               (#'q/vaadi-toimenpiteet-kuuluvat-urakkaan*
                [{::to/id 1}
                 {::to/id 2}
                 {::to/id 3}]
                #{1 2 3}
                nil))))

(deftest hinnoittelu-ilman-poistettuja-hintoja
  (is (= {::h/hinnat [{::m/poistettu? false ::hinta/id 3}]}
         (#'q/hinnoittelu-ilman-poistettuja-hintoja
           {::h/hinnat [{::m/poistettu? true ::hinta/id 1}
                        {::m/poistettu? true ::hinta/id 2}
                        {::m/poistettu? false ::hinta/id 3}]}))))

(deftest hae-hinnoittelut
  (is (= [{::h/hintaryhma? true
           ::h/hinnat [{::m/poistettu? false ::hinta/id 3}]}]
         (q/hae-hinnoittelut
           [{::h/hinnoittelut {::h/hintaryhma? true
                               ::h/hinnat [{::m/poistettu? true ::hinta/id 1}
                                           {::m/poistettu? true ::hinta/id 2}
                                           {::m/poistettu? false ::hinta/id 3}]}}
            {::h/hinnoittelut {::h/hintaryhma? false
                               ::h/hinnat [{::m/poistettu? true ::hinta/id 1}
                                           {::m/poistettu? true ::hinta/id 2}
                                           {::m/poistettu? false ::hinta/id 3}]}}
            {::h/hinnoittelut {::h/hintaryhma? true
                               ::h/hinnat [{::m/poistettu? true ::hinta/id 1}
                                           {::m/poistettu? true ::hinta/id 2}
                                           {::m/poistettu? false ::hinta/id 3}]
                               ::m/poistettu? true}}]
           true)))

  (is (= [{::h/hintaryhma? false
           ::h/hinnat [{::m/poistettu? false ::hinta/id 3}]}
          {::h/hintaryhma? false
           :foo :bar
           ::h/hinnat []}]
         (q/hae-hinnoittelut
           [{::h/hinnoittelut {::h/hintaryhma? true
                               ::h/hinnat [{::m/poistettu? true ::hinta/id 1}
                                           {::m/poistettu? true ::hinta/id 2}
                                           {::m/poistettu? false ::hinta/id 3}]}}
            {::h/hinnoittelut {::h/hintaryhma? false
                               ::h/hinnat [{::m/poistettu? true ::hinta/id 1}
                                           {::m/poistettu? true ::hinta/id 2}
                                           {::m/poistettu? false ::hinta/id 3}]}}
            {::h/hinnoittelut {::h/hintaryhma? false
                               :foo :bar
                               ::h/hinnat [{::m/poistettu? true ::hinta/id 1}
                                           {::m/poistettu? true ::hinta/id 2}
                                           {::m/poistettu? true ::hinta/id 3}]}}
            {::h/hinnoittelut {::h/hintaryhma? false
                               ::h/hinnat [{::m/poistettu? true ::hinta/id 1}
                                           {::m/poistettu? true ::hinta/id 2}
                                           {::m/poistettu? false ::hinta/id 3}]
                               ::m/poistettu? true}}
            {::h/hinnoittelut {::h/hintaryhma? true
                               ::h/hinnat [{::m/poistettu? true ::hinta/id 1}
                                           {::m/poistettu? true ::hinta/id 2}
                                           {::m/poistettu? false ::hinta/id 3}]
                               ::m/poistettu? true}}]
           false))))

(deftest toimenpide-siistitylla-hintatiedolla
  (is (= [{:foobar {::h/hintaryhma? false
                    ::h/hinnat [{::m/poistettu? false ::hinta/id 3}]}
           ::to/hinnoittelu-linkit [{::h/hinnoittelut {::h/hintaryhma? true
                                                       ::h/hinnat [{::m/poistettu? true ::hinta/id 1}
                                                                   {::m/poistettu? true ::hinta/id 2}
                                                                   {::m/poistettu? false ::hinta/id 3}]}}
                                    {::h/hinnoittelut {::h/hintaryhma? false
                                                       ::h/hinnat [{::m/poistettu? true ::hinta/id 1}
                                                                   {::m/poistettu? true ::hinta/id 2}
                                                                   {::m/poistettu? false ::hinta/id 3}]}}
                                    {::h/hinnoittelut {::h/hintaryhma? false
                                                       :foo :bar
                                                       ::h/hinnat [{::m/poistettu? true ::hinta/id 1}
                                                                   {::m/poistettu? true ::hinta/id 2}
                                                                   {::m/poistettu? true ::hinta/id 3}]}}
                                    {::h/hinnoittelut {::h/hintaryhma? false
                                                       ::h/hinnat [{::m/poistettu? true ::hinta/id 1}
                                                                   {::m/poistettu? true ::hinta/id 2}
                                                                   {::m/poistettu? false ::hinta/id 3}]
                                                       ::m/poistettu? true}}
                                    {::h/hinnoittelut {::h/hintaryhma? true
                                                       ::h/hinnat [{::m/poistettu? true ::hinta/id 1}
                                                                   {::m/poistettu? true ::hinta/id 2}
                                                                   {::m/poistettu? false ::hinta/id 3}]
                                                       ::m/poistettu? true}}]}]
         (q/toimenpide-siistitylla-hintatiedolla
           false
           :foobar
           [{::to/hinnoittelu-linkit [{::h/hinnoittelut {::h/hintaryhma? true
                                                         ::h/hinnat [{::m/poistettu? true ::hinta/id 1}
                                                                     {::m/poistettu? true ::hinta/id 2}
                                                                     {::m/poistettu? false ::hinta/id 3}]}}
                                      {::h/hinnoittelut {::h/hintaryhma? false
                                                         ::h/hinnat [{::m/poistettu? true ::hinta/id 1}
                                                                     {::m/poistettu? true ::hinta/id 2}
                                                                     {::m/poistettu? false ::hinta/id 3}]}}
                                      {::h/hinnoittelut {::h/hintaryhma? false
                                                         :foo :bar
                                                         ::h/hinnat [{::m/poistettu? true ::hinta/id 1}
                                                                     {::m/poistettu? true ::hinta/id 2}
                                                                     {::m/poistettu? true ::hinta/id 3}]}}
                                      {::h/hinnoittelut {::h/hintaryhma? false
                                                         ::h/hinnat [{::m/poistettu? true ::hinta/id 1}
                                                                     {::m/poistettu? true ::hinta/id 2}
                                                                     {::m/poistettu? false ::hinta/id 3}]
                                                         ::m/poistettu? true}}
                                      {::h/hinnoittelut {::h/hintaryhma? true
                                                         ::h/hinnat [{::m/poistettu? true ::hinta/id 1}
                                                                     {::m/poistettu? true ::hinta/id 2}
                                                                     {::m/poistettu? false ::hinta/id 3}]
                                                         ::m/poistettu? true}}]}]))))

(deftest toimenpiteet-omalla-hinnoittelulla
  (is (= [{::to/oma-hinnoittelu {::h/hintaryhma? false
                    ::h/hinnat [{::m/poistettu? false ::hinta/id 3}]}
           ::to/hinnoittelu-linkit [{::h/hinnoittelut {::h/hintaryhma? true
                                                       ::h/hinnat [{::m/poistettu? true ::hinta/id 1}
                                                                   {::m/poistettu? true ::hinta/id 2}
                                                                   {::m/poistettu? false ::hinta/id 3}]}}
                                    {::h/hinnoittelut {::h/hintaryhma? false
                                                       ::h/hinnat [{::m/poistettu? true ::hinta/id 1}
                                                                   {::m/poistettu? true ::hinta/id 2}
                                                                   {::m/poistettu? false ::hinta/id 3}]}}
                                    {::h/hinnoittelut {::h/hintaryhma? false
                                                       :foo :bar
                                                       ::h/hinnat [{::m/poistettu? true ::hinta/id 1}
                                                                   {::m/poistettu? true ::hinta/id 2}
                                                                   {::m/poistettu? true ::hinta/id 3}]}}
                                    {::h/hinnoittelut {::h/hintaryhma? false
                                                       ::h/hinnat [{::m/poistettu? true ::hinta/id 1}
                                                                   {::m/poistettu? true ::hinta/id 2}
                                                                   {::m/poistettu? false ::hinta/id 3}]
                                                       ::m/poistettu? true}}
                                    {::h/hinnoittelut {::h/hintaryhma? true
                                                       ::h/hinnat [{::m/poistettu? true ::hinta/id 1}
                                                                   {::m/poistettu? true ::hinta/id 2}
                                                                   {::m/poistettu? false ::hinta/id 3}]
                                                       ::m/poistettu? true}}]}]
         (q/toimenpiteet-omalla-hinnoittelulla
           [{::to/hinnoittelu-linkit [{::h/hinnoittelut {::h/hintaryhma? true
                                                         ::h/hinnat [{::m/poistettu? true ::hinta/id 1}
                                                                     {::m/poistettu? true ::hinta/id 2}
                                                                     {::m/poistettu? false ::hinta/id 3}]}}
                                      {::h/hinnoittelut {::h/hintaryhma? false
                                                         ::h/hinnat [{::m/poistettu? true ::hinta/id 1}
                                                                     {::m/poistettu? true ::hinta/id 2}
                                                                     {::m/poistettu? false ::hinta/id 3}]}}
                                      {::h/hinnoittelut {::h/hintaryhma? false
                                                         :foo :bar
                                                         ::h/hinnat [{::m/poistettu? true ::hinta/id 1}
                                                                     {::m/poistettu? true ::hinta/id 2}
                                                                     {::m/poistettu? true ::hinta/id 3}]}}
                                      {::h/hinnoittelut {::h/hintaryhma? false
                                                         ::h/hinnat [{::m/poistettu? true ::hinta/id 1}
                                                                     {::m/poistettu? true ::hinta/id 2}
                                                                     {::m/poistettu? false ::hinta/id 3}]
                                                         ::m/poistettu? true}}
                                      {::h/hinnoittelut {::h/hintaryhma? true
                                                         ::h/hinnat [{::m/poistettu? true ::hinta/id 1}
                                                                     {::m/poistettu? true ::hinta/id 2}
                                                                     {::m/poistettu? false ::hinta/id 3}]
                                                         ::m/poistettu? true}}]}]))))

(deftest toimenpiteet-hintaryhmalla
  (is (= [{::to/hintaryhma {::h/hintaryhma? true
                            ::h/hinnat [{::m/poistettu? false ::hinta/id 3}]}
           ::to/hinnoittelu-linkit [{::h/hinnoittelut {::h/hintaryhma? true
                                                       ::h/hinnat [{::m/poistettu? true ::hinta/id 1}
                                                                   {::m/poistettu? true ::hinta/id 2}
                                                                   {::m/poistettu? false ::hinta/id 3}]}}
                                    {::h/hinnoittelut {::h/hintaryhma? false
                                                       ::h/hinnat [{::m/poistettu? true ::hinta/id 1}
                                                                   {::m/poistettu? true ::hinta/id 2}
                                                                   {::m/poistettu? false ::hinta/id 3}]}}
                                    {::h/hinnoittelut {::h/hintaryhma? false
                                                       :foo :bar
                                                       ::h/hinnat [{::m/poistettu? true ::hinta/id 1}
                                                                   {::m/poistettu? true ::hinta/id 2}
                                                                   {::m/poistettu? true ::hinta/id 3}]}}
                                    {::h/hinnoittelut {::h/hintaryhma? false
                                                       ::h/hinnat [{::m/poistettu? true ::hinta/id 1}
                                                                   {::m/poistettu? true ::hinta/id 2}
                                                                   {::m/poistettu? false ::hinta/id 3}]
                                                       ::m/poistettu? true}}
                                    {::h/hinnoittelut {::h/hintaryhma? true
                                                       ::h/hinnat [{::m/poistettu? true ::hinta/id 1}
                                                                   {::m/poistettu? true ::hinta/id 2}
                                                                   {::m/poistettu? false ::hinta/id 3}]
                                                       ::m/poistettu? true}}]}]
         (q/toimenpiteet-hintaryhmalla
           [{::to/hinnoittelu-linkit [{::h/hinnoittelut {::h/hintaryhma? true
                                                         ::h/hinnat [{::m/poistettu? true ::hinta/id 1}
                                                                     {::m/poistettu? true ::hinta/id 2}
                                                                     {::m/poistettu? false ::hinta/id 3}]}}
                                      {::h/hinnoittelut {::h/hintaryhma? false
                                                         ::h/hinnat [{::m/poistettu? true ::hinta/id 1}
                                                                     {::m/poistettu? true ::hinta/id 2}
                                                                     {::m/poistettu? false ::hinta/id 3}]}}
                                      {::h/hinnoittelut {::h/hintaryhma? false
                                                         :foo :bar
                                                         ::h/hinnat [{::m/poistettu? true ::hinta/id 1}
                                                                     {::m/poistettu? true ::hinta/id 2}
                                                                     {::m/poistettu? true ::hinta/id 3}]}}
                                      {::h/hinnoittelut {::h/hintaryhma? false
                                                         ::h/hinnat [{::m/poistettu? true ::hinta/id 1}
                                                                     {::m/poistettu? true ::hinta/id 2}
                                                                     {::m/poistettu? false ::hinta/id 3}]
                                                         ::m/poistettu? true}}
                                      {::h/hinnoittelut {::h/hintaryhma? true
                                                         ::h/hinnat [{::m/poistettu? true ::hinta/id 1}
                                                                     {::m/poistettu? true ::hinta/id 2}
                                                                     {::m/poistettu? false ::hinta/id 3}]
                                                         ::m/poistettu? true}}]}]))))

(deftest ilman-poistettuja-linkkeja
  (is (= [{::to/hinnoittelu-linkit [{::m/poistettu? false}
                                    {::m/poistettu? false}]}]
         (q/ilman-poistettuja-linkkeja
           [{::to/hinnoittelu-linkit [{::m/poistettu? true}
                                      {::m/poistettu? false}
                                      {::m/poistettu? false}]}]))))

(deftest toimenpiteet-tyotiedoilla
  (is (= [{::to/oma-hinnoittelu {::h/id 1
                                 ::h/tyot [{::tyo/hinnoittelu-id 1}]}}]
         (#'q/toimenpiteet-tyotiedoilla*
           [{::tyo/hinnoittelu-id 1}]
           [{::to/oma-hinnoittelu {::h/id 1}}]))))

(deftest hae-hinnoittelutiedot-toimenpiteille
  (is (= [{::to/oma-hinnoittelu {::m/poistettu? false
                                 ::h/hintaryhma? false
                                 ::h/hinnat [{::m/poistettu? false ::hinta/id 3}]}
           ::to/hintaryhma-id 1}]
         (#'q/hae-hinnoittelutiedot-toimenpiteille*
           [{::to/hinnoittelu-linkit [{::m/poistettu? true}
                                      {::m/poistettu? false
                                       ::h/hinnoittelut {::m/poistettu? true}}
                                      {::m/poistettu? false
                                       ::h/hinnoittelut {::m/poistettu? false
                                                         ::h/hintaryhma? true
                                                         ::h/id 1
                                                         ::h/hinnat [{::m/poistettu? true ::hinta/id 1}
                                                                     {::m/poistettu? true ::hinta/id 2}
                                                                     {::m/poistettu? false ::hinta/id 3}]}}
                                      {::m/poistettu? false
                                       ::h/hinnoittelut {::m/poistettu? false
                                                         ::h/hintaryhma? false
                                                         ::h/hinnat [{::m/poistettu? true ::hinta/id 1}
                                                                     {::m/poistettu? true ::hinta/id 2}
                                                                     {::m/poistettu? false ::hinta/id 3}]}}]}]))))

(deftest suodata-vikakorjaukset
  (testing "Jos vikailmoitukset?-ehto on false, ei tehdä mitään"
    (is (= [1 2 3 4]
          (#'q/suodata-vikakorjaukset
            [1 2 3 4]
            false))))

  (is (= [{::to/reimari-viat [{:foo :bar}] ::to/id 2}
          {::to/reimari-viat [1 2 3] ::to/id 3}]
         (#'q/suodata-vikakorjaukset
           [{::to/reimari-viat [] ::to/id 1}
            {::to/reimari-viat [{:foo :bar}] ::to/id 2}
            {::to/reimari-viat [1 2 3] ::to/id 3}]
           true))))

(deftest toimenpiteet-hintatiedoilla
  (is (= [{::to/id 1
           :baz :bar
           :foo :bar}
          {::to/id 2}]
         (#'q/toimenpiteet-hintatiedoilla*
           [{::to/id 1
             :baz :bar}
            {::to/id 3}]
           [{::to/id 1
             :foo :bar}
            {::to/id 2}]))))

(deftest lisaa-toimenpiteen-komponentit
  (is (= [{::to/id 1
           ::to/komponentit
           [{::tkomp/id 1
             ::tkomp/sarjanumero "1"
             ::tkomp/valiaikainen "2"
             ::tkomp/lisatiedot "Foo"
             ::tkomp/komponenttityyppi "Bar"
             ::tkomp/turvalaitenro "123"
             ::komp-tila/tilakoodi "1"}]}]
         (#'q/lisaa-toimenpiteen-komponentit*
           [{::to/id 1}]
           [{::komp-tila/toimenpide-id 1
             ::komp-tila/komponentti-id 1
             ::komp-tila/tilakoodi "1"}]
           [{::tkomp/id 1
             ::tkomp/sarjanumero "1"
             ::tkomp/valiaikainen "2"
             ::tkomp/lisatiedot "Foo"
             ::tkomp/komponenttityyppi "Bar"
             ::tkomp/turvalaitenro "123"}])))

  (is (= [{::to/id 1
           ::to/komponentit
           [{::tkomp/id 1
             ::tkomp/sarjanumero "1"
             ::tkomp/valiaikainen "2"
             ::tkomp/lisatiedot "Foo"
             ::tkomp/komponenttityyppi "Bar"
             ::tkomp/turvalaitenro "123"
             ::komp-tila/tilakoodi "1"}]}
          {::to/id 2
           ::to/komponentit
           [{::tkomp/id 1
             ::tkomp/sarjanumero "1"
             ::tkomp/valiaikainen "2"
             ::tkomp/lisatiedot "Foo"
             ::tkomp/komponenttityyppi "Bar"
             ::tkomp/turvalaitenro "123"
             ::komp-tila/tilakoodi "1"}]}]
         (#'q/lisaa-toimenpiteen-komponentit*
           [{::to/id 1}
            {::to/id 2}]
           [{::komp-tila/toimenpide-id 1
             ::komp-tila/komponentti-id 1
             ::komp-tila/tilakoodi "1"}
            {::komp-tila/toimenpide-id 2
             ::komp-tila/komponentti-id 1
             ::komp-tila/tilakoodi "1"}]
           [{::tkomp/id 1
             ::tkomp/sarjanumero "1"
             ::tkomp/valiaikainen "2"
             ::tkomp/lisatiedot "Foo"
             ::tkomp/komponenttityyppi "Bar"
             ::tkomp/turvalaitenro "123"}])))

  (testing "Toimenpiteen komponentille voi olla monta tilamuutosta"
    (is (= [{::to/id 1
            ::to/komponentit
            [{::tkomp/id 1
              ::tkomp/sarjanumero "1"
              ::tkomp/valiaikainen "2"
              ::tkomp/lisatiedot "Foo"
              ::tkomp/komponenttityyppi "Bar"
              ::tkomp/turvalaitenro "123"
              ::komp-tila/tilakoodi "1"}
             {::tkomp/id 1
              ::tkomp/sarjanumero "1"
              ::tkomp/valiaikainen "2"
              ::tkomp/lisatiedot "Foo"
              ::tkomp/komponenttityyppi "Bar"
              ::tkomp/turvalaitenro "123"
              ::komp-tila/tilakoodi "2"}]}]
          (#'q/lisaa-toimenpiteen-komponentit*
            [{::to/id 1}]
            [{::komp-tila/toimenpide-id 1
              ::komp-tila/komponentti-id 1
              ::komp-tila/tilakoodi "1"}
             {::komp-tila/toimenpide-id 1
              ::komp-tila/komponentti-id 1
              ::komp-tila/tilakoodi "2"}]
            [{::tkomp/id 1
              ::tkomp/sarjanumero "1"
              ::tkomp/valiaikainen "2"
              ::tkomp/lisatiedot "Foo"
              ::tkomp/komponenttityyppi "Bar"
              ::tkomp/turvalaitenro "123"}]))))

  (testing "Ei liitetä tiloja joille ei ole komponenttia"
    (is (= [{::to/id 1
            ::to/komponentit
            [{::tkomp/id 1
              ::tkomp/sarjanumero "1"
              ::tkomp/valiaikainen "2"
              ::tkomp/lisatiedot "Foo"
              ::tkomp/komponenttityyppi "Bar"
              ::tkomp/turvalaitenro "123"
              ::komp-tila/tilakoodi "1"}]}]
          (#'q/lisaa-toimenpiteen-komponentit*
            [{::to/id 1}]
            [{::komp-tila/toimenpide-id 1
              ::komp-tila/komponentti-id 1
              ::komp-tila/tilakoodi "1"}
             {::komp-tila/toimenpide-id 1
              ::komp-tila/komponentti-id 2
              ::komp-tila/tilakoodi "1"}]
            [{::tkomp/id 1
              ::tkomp/sarjanumero "1"
              ::tkomp/valiaikainen "2"
              ::tkomp/lisatiedot "Foo"
              ::tkomp/komponenttityyppi "Bar"
              ::tkomp/turvalaitenro "123"}]))))

  (testing "Ei liitetä komponenttia, jolle ei ole tilamuutosta toimenpiteessä"
    (is (= [{::to/id 1
            ::to/komponentit
            [{::tkomp/id 1
              ::tkomp/sarjanumero "1"
              ::tkomp/valiaikainen "2"
              ::tkomp/lisatiedot "Foo"
              ::tkomp/komponenttityyppi "Bar"
              ::tkomp/turvalaitenro "123"
              ::komp-tila/tilakoodi "1"}]}]
          (#'q/lisaa-toimenpiteen-komponentit*
            [{::to/id 1}]
            [{::komp-tila/toimenpide-id 1
              ::komp-tila/komponentti-id 1
              ::komp-tila/tilakoodi "1"}]
            [{::tkomp/id 1
              ::tkomp/sarjanumero "1"
              ::tkomp/valiaikainen "2"
              ::tkomp/lisatiedot "Foo"
              ::tkomp/komponenttityyppi "Bar"
              ::tkomp/turvalaitenro "123"}
             {::tkomp/id 2
              ::tkomp/sarjanumero "1"
              ::tkomp/valiaikainen "2"
              ::tkomp/lisatiedot "Foo"
              ::tkomp/komponenttityyppi "Bar"
              ::tkomp/turvalaitenro "123"}])))

    (is (= [{::to/id 1
             ::to/komponentit
             [{::tkomp/id 1
               ::tkomp/sarjanumero "1"
               ::tkomp/valiaikainen "2"
               ::tkomp/lisatiedot "Foo"
               ::tkomp/komponenttityyppi "Bar"
               ::tkomp/turvalaitenro "123"
               ::komp-tila/tilakoodi "1"}]}]
           (#'q/lisaa-toimenpiteen-komponentit*
             [{::to/id 1}]
             [{::komp-tila/toimenpide-id 1
               ::komp-tila/komponentti-id 1
               ::komp-tila/tilakoodi "1"}
              {::komp-tila/toimenpide-id 2
               ::komp-tila/komponentti-id 2
               ::komp-tila/tilakoodi "1"}]
             [{::tkomp/id 1
               ::tkomp/sarjanumero "1"
               ::tkomp/valiaikainen "2"
               ::tkomp/lisatiedot "Foo"
               ::tkomp/komponenttityyppi "Bar"
               ::tkomp/turvalaitenro "123"}
              {::tkomp/id 2
               ::tkomp/sarjanumero "1"
               ::tkomp/valiaikainen "2"
               ::tkomp/lisatiedot "Foo"
               ::tkomp/komponenttityyppi "Bar"
               ::tkomp/turvalaitenro "123"}])))))

(deftest toimenpiteiden-liite-idt
  (is (= {1 [1 2 3]
          2 [1 5]}
         (#'q/toimenpiteiden-liite-idt*
           [{::to/toimenpide-id 1
             ::to/liite-id 1}
            {::to/toimenpide-id 1
             ::to/liite-id 2}
            {::to/toimenpide-id 1
             ::to/liite-id 3}
            {::to/toimenpide-id 2
             ::to/liite-id 1}
            {::to/toimenpide-id 2
             ::to/liite-id 5}]))))

(deftest lisaa-liitteet
  (is (= [{::to/id 1
           ::to/liitteet [{:id 1}
                          {:id 2}
                          {:id 3}]}]
         (#'q/lisaa-liitteet*
           [{::to/id 1}]
           {1 [1 2 3]}
           [{::liite/id 1}
            {::liite/id 2}
            {::liite/id 3}])))

  (testing "Ei liitteitä -> tyhjä sekvenssi"
    (is (= [{::to/id 1
            ::to/liitteet [{:id 1}
                           {:id 2}
                           {:id 3}]}
           {::to/id 2
            ::to/liitteet []}]
          (#'q/lisaa-liitteet*
            [{::to/id 1}
             {::to/id 2}]
            {1 [1 2 3]}
            [{::liite/id 1}
             {::liite/id 2}
             {::liite/id 3}]))))

  (testing "Ylimääräiset liitteet eivät tule mukaan"
    (is (= [{::to/id 1
            ::to/liitteet [{:id 1}
                           {:id 2}
                           {:id 3}]}]
          (#'q/lisaa-liitteet*
            [{::to/id 1}]
            {1 [1 2 3]}
            [{::liite/id 1}
             {::liite/id 2}
             {::liite/id 3}
             {::liite/id 4}]))))

  (is (= [{::to/id 1
           ::to/liitteet [{:id 1}
                          {:id 2}
                          {:id 3}]}]
         (#'q/lisaa-liitteet*
           [{::to/id 1}]
           {1 [1 2 3]
            2 [4 5 6]}
           [{::liite/id 1}
            {::liite/id 2}
            {::liite/id 3}])))

  (is (= [{::to/id 1
           ::to/liitteet [{:id 1}
                          {:id 2}
                          {:id 3}]}
          {::to/id 2
           ::to/liitteet [{:id 3}]}]
         (#'q/lisaa-liitteet*
           [{::to/id 1}
            {::to/id 2}]
           {1 [1 2 3]
            2 [3]}
           [{::liite/id 1}
            {::liite/id 2}
            {::liite/id 3}]))))